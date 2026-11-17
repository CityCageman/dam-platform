package com.dam.integration.service;

import com.dam.common.exception.BusinessException;
import com.dam.integration.entity.IntegrationJob;
import com.dam.integration.entity.IntegrationRun;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 数据集成诊断服务
 * <p>
 * 在不改动任务触发与执行链路的前提下，提供三项运维能力：
 * <ul>
 *   <li>连接测试：分别使用 sourceConfig/targetConfig 建立真实 JDBC 连接并执行
 *       {@code SELECT 1}，返回两端的 ok/产品/版本/耗时/错误；单端失败不抛 500，
 *       统一由 data.ok=false 表达；</li>
 *   <li>数据预览：只读源端，执行 source query 或 {@code SELECT * FROM schema.table}，
 *       最多返回 100 行；不打开、不写入目标端，也不创建 integration_run；</li>
 *   <li>运行重试：仅允许已结束的历史运行记录（FAILED/SUCCESS/CANCELLED）重试，
 *       复用 {@link IntegrationJobService#triggerJob(Long)} 创建新的 PENDING 运行记录。</li>
 * </ul>
 * 配置解析、URL 白名单、标识符白名单、query 只读白名单与密码解密全部复用
 * {@link IntegrationExecutionService}，不在这里另写一套校验；密码只经 JDBC Properties
 * 传给驱动，不进入响应与日志。探测在独立守护线程中执行，并通过 Future + JDBC
 * connectTimeout/socketTimeout/queryTimeout 实现双重超时保护。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationDiagnosticsService {

    /** 预览默认返回行数 */
    private static final int DEFAULT_PREVIEW_LIMIT = 20;

    /** 预览最小行数 */
    private static final int MIN_PREVIEW_LIMIT = 1;

    /** 预览最大行数 */
    private static final int MAX_PREVIEW_LIMIT = 100;

    /** 连接测试建立连接超时（秒） */
    private static final int PROBE_CONNECT_TIMEOUT_SECONDS = 10;

    /** 连接测试网络读超时（秒） */
    private static final int PROBE_SOCKET_TIMEOUT_SECONDS = 15;

    /** 连接测试 SELECT 1 查询超时（秒） */
    private static final int PROBE_QUERY_TIMEOUT_SECONDS = 10;

    /** 连接测试整体超时（毫秒），防止驱动/DNS 等不受控阻塞 */
    private static final long CONNECTION_TEST_TIMEOUT_MILLIS = 20_000L;

    /** 预览建立连接超时（秒） */
    private static final int PREVIEW_CONNECT_TIMEOUT_SECONDS = 10;

    /** 预览网络读超时（秒） */
    private static final int PREVIEW_SOCKET_TIMEOUT_SECONDS = 30;

    /** 预览查询超时（秒） */
    private static final int PREVIEW_QUERY_TIMEOUT_SECONDS = 15;

    /** 预览整体超时（毫秒），防止驱动/DNS 等不受控阻塞 */
    private static final long PREVIEW_TIMEOUT_MILLIS = 30_000L;

    /** 脱敏后的取值 */
    private static final String MASKED_VALUE = "***";

    /** 列名包含这些词（不区分大小写）时，行值替换为 *** */
    private static final Pattern SENSITIVE_COLUMN_PATTERN =
            Pattern.compile("(?i)(password|pwd|secret|token)");

    /** 允许重试的运行终态；PENDING/RUNNING 属于活跃运行，禁止重试 */
    private static final Set<String> RETRYABLE_RUN_STATUSES = Set.of(
            IntegrationRun.STATUS_FAILED,
            IntegrationRun.STATUS_SUCCESS,
            "CANCELLED");

    /** 集成任务管理服务（复用 triggerJob 创建新运行记录） */
    private final IntegrationJobService jobService;

    /** 真实 JDBC 执行服务（复用配置解析、URL/标识符/SQL 白名单与连接参数） */
    private final IntegrationExecutionService executionService;

    /** 连接测试/数据预览探测线程池（由 IntegrationAsyncConfig 提供） */
    @Resource(name = "integrationProbeExecutor")
    private ExecutorService probeExecutor;

    /**
     * 连接测试
     * <p>
     * 分别读取 sourceConfig/targetConfig，真实建立 JDBC 连接并执行 {@code SELECT 1}。
     * 任意一端失败（含配置不合法、类型不是 JDBC、连接失败、超时）都只把该端标记为
     * ok=false，接口仍返回 code=200，不向上抛出 500，也不创建运行记录。
     * 任务不存在仍按既有约定返回 404。
     *
     * @param jobId 集成任务 ID
     * @return jobId、整体 ok 以及 source/target 两端的探测结果
     */
    public Map<String, Object> testConnection(Long jobId) {
        IntegrationJob job = jobService.getJob(jobId);
        Map<String, Object> source = probeSide(job, true);
        Map<String, Object> target = probeSide(job, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("ok", Boolean.TRUE.equals(source.get("ok"))
                && Boolean.TRUE.equals(target.get("ok")));
        data.put("source", source);
        data.put("target", target);
        return data;
    }

    /**
     * 数据预览
     * <p>
     * 仅支持 JDBC 源端，复用执行服务的 source query / schema.table 规则构造只读 SQL；
     * 只连接源端并读取最多 limit 行，不写目标端、不写 integration_run。
     * 列名包含 password/pwd/secret/token 的列，其行值统一替换为 {@code ***}。
     * 配置或查询失败返回 400（脱敏后），超时同样返回可读错误，不返回 500。
     *
     * @param jobId 集成任务 ID
     * @param limit 返回行数，默认 20，超过 1~100 时收敛到边界值
     * @return jobId、columns、rows、rowCount、truncated
     */
    public Map<String, Object> preview(Long jobId, Integer limit) {
        IntegrationJob job = jobService.getJob(jobId);
        // 与现有列表接口保持一致：非法 limit 收敛到 1~100，而不是让请求失败
        int size = Math.clamp(limit == null ? DEFAULT_PREVIEW_LIMIT : limit,
                MIN_PREVIEW_LIMIT, MAX_PREVIEW_LIMIT);
        String sourceType = IntegrationExecutionService.normalizeType(job.getSourceType());
        if (!IntegrationExecutionService.TYPE_JDBC.equals(sourceType)) {
            throw new BusinessException(400,
                    "数据预览仅支持 JDBC 源端，当前源端类型: " + job.getSourceType());
        }
        IntegrationExecutionService.Endpoint source;
        try {
            source = executionService.parseEndpoint(job.getSourceConfig(), "sourceConfig", true);
        } catch (RuntimeException e) {
            throw new BusinessException(400,
                    "数据预览配置不合法: " + IntegrationExecutionService.buildErrorMessage(e));
        }

        try {
            PreviewData preview = callWithTimeout("数据预览", PREVIEW_TIMEOUT_MILLIS,
                    () -> readPreview(source, size));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("jobId", job.getId());
            data.put("limit", size);
            data.put("columns", preview.columns());
            data.put("rows", preview.rows());
            data.put("rowCount", preview.rows().size());
            data.put("truncated", preview.truncated());
            log.info("集成任务数据预览完成: jobId={}, limit={}, rowCount={}, truncated={}",
                    job.getId(), size, preview.rows().size(), preview.truncated());
            return data;
        } catch (Exception e) {
            String message = IntegrationExecutionService.buildErrorMessage(e);
            log.warn("集成任务数据预览失败: jobId={}, error={}", job.getId(), message);
            throw new BusinessException(400, "数据预览失败: " + message);
        }
    }

    /**
     * 运行重试
     * <p>
     * 仅允许 FAILED/SUCCESS/CANCELLED 的历史运行重试；PENDING/RUNNING 说明任务仍有
     * 活跃运行，返回 409。校验通过后复用 {@link IntegrationJobService#triggerJob(Long)}
     * 创建新的 PENDING 运行记录（活跃运行与并发检查、run_no 分配都在原方法内完成），
     * 返回新 runId，并把原 runId 放在 retriedFromRunId 中。
     *
     * @param runId 原运行记录 ID
     * @return 新运行记录信息（runId/jobId/runNo/status/accepted）与原运行记录 ID
     */
    public Map<String, Object> retryRun(Long runId) {
        IntegrationRun run = executionService.getRun(runId);
        String status = run.getStatus() == null
                ? null : run.getStatus().trim().toUpperCase(Locale.ROOT);
        if (IntegrationRun.STATUS_PENDING.equals(status)
                || IntegrationRun.STATUS_RUNNING.equals(status)) {
            throw new BusinessException(409,
                    "运行记录仍在等待执行或正在执行，不允许重试: runId=" + runId);
        }
        if (!RETRYABLE_RUN_STATUSES.contains(status)) {
            throw new BusinessException(400,
                    "仅允许重试已结束的运行记录（FAILED/SUCCESS/CANCELLED），当前状态: "
                            + run.getStatus());
        }

        Map<String, Object> result = new LinkedHashMap<>(jobService.triggerJob(run.getJobId()));
        result.put("newRunId", result.get("runId"));
        result.put("retriedFromRunId", runId);
        log.info("集成运行重试受理: previousRunId={}, newRunId={}, jobId={}",
                runId, result.get("runId"), run.getJobId());
        return result;
    }

    // ------------------------------------------------------------------
    //  连接测试
    // ------------------------------------------------------------------

    /**
     * 探测单个端点；所有失败都收敛为 ok=false + error，不向上抛出
     *
     * @param job    集成任务
     * @param source true=源端，false=目标端
     * @return ok/databaseProduct/version/elapsedMs/error
     */
    private Map<String, Object> probeSide(IntegrationJob job, boolean source) {
        String side = source ? "源端" : "目标端";
        String label = source ? "sourceConfig" : "targetConfig";
        String rawType = source ? job.getSourceType() : job.getTargetType();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("databaseProduct", null);
        result.put("version", null);
        result.put("elapsedMs", 0L);
        result.put("error", null);

        long started = System.nanoTime();
        try {
            String type = IntegrationExecutionService.normalizeType(rawType);
            if (type == null) {
                throw new IllegalArgumentException(side + "类型不能为空");
            }
            if (!IntegrationExecutionService.TYPE_JDBC.equals(type)) {
                throw new IllegalArgumentException("不支持的" + side + "类型: " + rawType
                        + "，当前仅支持 JDBC");
            }
            // 连接测试只需要连接信息；已提供的 schema/table/query 仍会走执行服务的白名单校验
            IntegrationExecutionService.Endpoint endpoint =
                    executionService.parseEndpoint(
                            source ? job.getSourceConfig() : job.getTargetConfig(),
                            label, source, false);
            ProbeInfo info = callWithTimeout(side + "连接测试", CONNECTION_TEST_TIMEOUT_MILLIS,
                    () -> probe(endpoint, side));
            result.put("ok", true);
            result.put("databaseProduct", info.product());
            result.put("version", info.version());
        } catch (Exception e) {
            String message = IntegrationExecutionService.buildErrorMessage(e);
            result.put("error", message);
            log.warn("集成任务{}连接测试失败: jobId={}, error={}", side, job.getId(), message);
        } finally {
            result.put("elapsedMs", elapsedMillis(started));
        }
        return result;
    }

    /**
     * 建立连接并执行 {@code SELECT 1}
     *
     * @param endpoint 已通过执行服务白名单校验的端点配置
     * @param side     源端/目标端，用于产品校验提示
     * @return 数据库产品名与版本
     * @throws SQLException 连接或查询失败
     */
    private ProbeInfo probe(IntegrationExecutionService.Endpoint endpoint, String side)
            throws SQLException {
        try (Connection connection = IntegrationExecutionService.openConnection(
                endpoint, true, PROBE_CONNECT_TIMEOUT_SECONDS, PROBE_SOCKET_TIMEOUT_SECONDS)) {
            DatabaseMetaData metaData = connection.getMetaData();
            IntegrationExecutionService.requirePostgres(metaData, side);
            String product = metaData.getDatabaseProductName();
            String version = metaData.getDatabaseProductVersion();
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(PROBE_QUERY_TIMEOUT_SECONDS);
                try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    if (!resultSet.next()) {
                        throw new SQLException("SELECT 1 未返回结果");
                    }
                    resultSet.getInt(1);
                }
            }
            return new ProbeInfo(product, version);
        }
    }

    // ------------------------------------------------------------------
    //  数据预览
    // ------------------------------------------------------------------

    /**
     * 只读源端查询并组装预览数据
     * <p>
     * 使用 {@code setMaxRows(limit + 1)} 多取一行判断是否被截断；不含任何目标端连接与写入。
     *
     * @param source 源端配置
     * @param limit  最大返回行数（已收敛到 1~100）
     * @return 列名、行数据与截断标记
     * @throws SQLException 连接或查询失败
     */
    private PreviewData readPreview(IntegrationExecutionService.Endpoint source, int limit)
            throws SQLException {
        try (Connection connection = IntegrationExecutionService.openConnection(
                source, true, PREVIEW_CONNECT_TIMEOUT_SECONDS, PREVIEW_SOCKET_TIMEOUT_SECONDS)) {
            // 预览只读：显式只读事务，连接关闭时回滚，不产生任何写入
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            DatabaseMetaData metaData = connection.getMetaData();
            IntegrationExecutionService.requirePostgres(metaData, "源端");
            String sql = IntegrationExecutionService.buildSourceSql(source, metaData);

            try (Statement statement = connection.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setFetchSize(limit);
                statement.setQueryTimeout(PREVIEW_QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(limit + 1);
                try (ResultSet resultSet = statement.executeQuery(sql)) {
                    ResultSetMetaData columnMeta = resultSet.getMetaData();
                    int columnCount = columnMeta.getColumnCount();
                    if (columnCount < 1) {
                        throw new SQLException("源查询没有返回任何列");
                    }

                    List<String> columns = new ArrayList<>(columnCount);
                    boolean[] sensitiveColumns = new boolean[columnCount];
                    for (int i = 1; i <= columnCount; i++) {
                        String label = columnMeta.getColumnLabel(i);
                        if (!IntegrationExecutionService.hasText(label)) {
                            label = columnMeta.getColumnName(i);
                        }
                        if (!IntegrationExecutionService.hasText(label)) {
                            label = "column_" + i;
                        }
                        columns.add(label);
                        sensitiveColumns[i - 1] =
                                SENSITIVE_COLUMN_PATTERN.matcher(label).find();
                    }

                    List<List<Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (resultSet.next()) {
                        if (rows.size() >= limit) {
                            // 已读满 limit 行，仍能再读到一行，说明结果被截断
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(sensitiveColumns[i - 1]
                                    ? MASKED_VALUE : toJsonValue(resultSet.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return new PreviewData(columns, rows, truncated);
                }
            }
        }
    }

    /**
     * 把 JDBC 取值转换为可 JSON 序列化的简单值
     *
     * @param value JDBC 原始值
     * @return 字符串/数字/布尔/null 或二进制 Base64 文本
     */
    private static Object toJsonValue(Object value) {
        if (value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        // Timestamp/Date/PGobject/Array 等使用驱动的可读文本表示
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------
    //  超时与通用工具
    // ------------------------------------------------------------------

    /**
     * 在探测线程池中执行任务，并施加整体超时
     * <p>
     * 超时/中断/线程池拒绝都会转换为可读的 {@link ProbeException}；
     * 工作线程内的 JDBC 异常原样抛出，由调用方脱敏。
     *
     * @param action        操作名称，用于错误提示
     * @param timeoutMillis 整体超时（毫秒）
     * @param task          探测任务
     * @param <T>           结果类型
     * @return 任务结果
     * @throws Exception 任务执行失败
     */
    private <T> T callWithTimeout(String action, long timeoutMillis, Callable<T> task)
            throws Exception {
        Future<T> future;
        try {
            future = probeExecutor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new ProbeException(action + "繁忙，请稍后重试");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProbeException(action + "超时（超过 " + timeoutMillis + "ms）");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProbeException(action + "被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new ProbeException(action + "失败: " + cause);
        }
    }

    /**
     * 计算耗时（毫秒）
     *
     * @param startedNanos {@link System#nanoTime()} 起点
     * @return 非负耗时
     */
    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    /**
     * 连接测试结果
     */
    private record ProbeInfo(String product, String version) {
    }

    /**
     * 预览数据
     */
    private record PreviewData(List<String> columns, List<List<Object>> rows, boolean truncated) {
    }

    /**
     * 探测超时/中断/繁忙异常
     */
    private static final class ProbeException extends RuntimeException {
        private ProbeException(String message) {
            super(message);
        }
    }
}
