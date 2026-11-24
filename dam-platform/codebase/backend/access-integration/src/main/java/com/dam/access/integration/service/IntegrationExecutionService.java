package com.dam.access.integration.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.security.CredentialCipher;
import com.dam.access.integration.client.TaskCenterClient;
import com.dam.access.integration.entity.IntegrationJob;
import com.dam.access.integration.entity.IntegrationRun;
import com.dam.access.integration.repository.IntegrationJobMapper;
import com.dam.access.integration.repository.IntegrationRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据集成执行服务
 * <p>
 * 基础版真实 JDBC 全量同步执行器：
 * <ul>
 *   <li>仅支持 sourceType/targetType=JDBC、syncMode=FULL；其他类型/模式明确报错；</li>
 *   <li>源端执行 query（若配置）或 {@code SELECT * FROM schema.table}；</li>
 *   <li>目标端按源结果集列名匹配目标表列，使用 PreparedStatement batch 追加写入；</li>
 *   <li>不做 truncate/delete，按批提交，失败时已提交批次保留但运行记录置 FAILED；</li>
 *   <li>SQL 标识符严格白名单校验并用数据库标识符引号包裹；query 仅允许单条 SELECT。</li>
 * </ul>
 * 运行记录状态由本服务维护：PENDING → RUNNING → SUCCESS/FAILED。异常信息脱敏并截断
 * 到 2000 字符，密码不会写入日志或错误信息。
 * <p>
 * 运行开始/结束会尽力回写统一任务中心（TaskCenter）：instanceId 仅保存在执行方法的局部变量中，
 * 不新增数据库字段；回调失败只记录 warn，不改变集成任务的状态与成功判定。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
public class IntegrationExecutionService {

    /** 唯一支持的数据源类型 */
    static final String TYPE_JDBC = "JDBC";

    /** 唯一支持的同步模式 */
    private static final String MODE_FULL = "FULL";

    /** 默认批大小 */
    private static final int DEFAULT_BATCH_SIZE = 500;

    /** 批大小上限 */
    private static final int MAX_BATCH_SIZE = 1000;

    /** 连接超时（秒），同时写入 PostgreSQL 驱动的 connectTimeout / loginTimeout */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** 网络读超时（秒），避免网络中断后线程永久阻塞 */
    private static final int SOCKET_TIMEOUT_SECONDS = 600;

    /** SQL 查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 300;

    /** 错误信息最大长度 */
    private static final int MAX_ERROR_LENGTH = 2000;

    /** 标识符最大长度（PostgreSQL 默认为 63） */
    private static final int MAX_IDENTIFIER_LENGTH = 63;

    /** URL 最大长度 */
    private static final int MAX_URL_LENGTH = 2048;

    /** 用户名最大长度 */
    private static final int MAX_USERNAME_LENGTH = 128;

    /** 密码最大长度 */
    private static final int MAX_PASSWORD_LENGTH = 1024;

    /** query 最大长度 */
    private static final int MAX_QUERY_LENGTH = 65535;

    /** 运行记录列表默认条数 */
    private static final int DEFAULT_RUN_LIMIT = 50;

    /** 运行记录列表最大条数 */
    private static final int MAX_RUN_LIMIT = 200;

    /** 仅允许 PostgreSQL JDBC URL */
    private static final String POSTGRESQL_URL_PREFIX = "jdbc:postgresql:";

    /** 标识符严格白名单，杜绝拼接注入 */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** query 中禁止出现的写操作/DDL 关键字（字符串字面量与引号标识符会先被屏蔽） */
    private static final Pattern FORBIDDEN_QUERY_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|MERGE|REPLACE|"
                    + "CALL|EXECUTE|COPY|VACUUM|INTO)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 确保 PostgreSQL 驱动可用（依赖已由 pom 提供） */
    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("缺少 PostgreSQL JDBC 驱动: " + e.getMessage());
        }
    }

    /** 集成任务 Mapper */
    private final IntegrationJobMapper jobMapper;

    /** 运行记录 Mapper */
    private final IntegrationRunMapper runMapper;

    /** 平台事务管理器，用于把状态变更做成短事务，避免长事务占用连接 */
    private final PlatformTransactionManager transactionManager;

    /** 统一任务中心回调客户端（旁路能力，失败不影响集成执行） */
    private final TaskCenterClient taskCenterClient;

    /** 配置凭据加解密组件（执行前解密 sourceConfig/targetConfig 的 password） */
    private final CredentialCipher credentialCipher;

    /** 孤儿运行判定阈值（分钟）：服务崩溃会留下 PENDING/RUNNING 记录，超时后标记 FAILED */
    @Value("${dam.integration.stale-run-minutes:30}")
    private long staleRunMinutes;

    /**
     * 构造执行服务
     *
     * @param jobMapper         任务 Mapper
     * @param runMapper         运行记录 Mapper
     * @param transactionManager 事务管理器
     * @param taskCenterClient  统一任务中心回调客户端
     * @param credentialCipher  配置凭据加解密组件
     */
    public IntegrationExecutionService(IntegrationJobMapper jobMapper,
                                       IntegrationRunMapper runMapper,
                                       PlatformTransactionManager transactionManager,
                                       TaskCenterClient taskCenterClient,
                                       CredentialCipher credentialCipher) {
        this.jobMapper = jobMapper;
        this.runMapper = runMapper;
        this.transactionManager = transactionManager;
        this.taskCenterClient = taskCenterClient;
        this.credentialCipher = credentialCipher;
    }

    /**
     * 校验任务类型与同步模式是否受支持
     * <p>
     * 在 trigger 事务内调用，遇到不支持的类型/模式直接返回 400，不创建运行记录。
     *
     * @param job 集成任务
     * @throws BusinessException 类型或同步模式不受支持
     */
    public void validateSupported(IntegrationJob job) {
        if (job == null) {
            throw new BusinessException(400, "集成任务不能为空");
        }
        String sourceType = normalizeType(job.getSourceType());
        if (sourceType == null) {
            throw new BusinessException(400, "源端类型不能为空");
        }
        if (!TYPE_JDBC.equals(sourceType)) {
            throw new BusinessException(400,
                    "不支持的源端类型: " + job.getSourceType() + "，当前仅支持 JDBC");
        }
        String targetType = normalizeType(job.getTargetType());
        if (targetType == null) {
            throw new BusinessException(400, "目标端类型不能为空");
        }
        if (!TYPE_JDBC.equals(targetType)) {
            throw new BusinessException(400,
                    "不支持的目标端类型: " + job.getTargetType() + "，当前仅支持 JDBC");
        }
        String syncMode = normalizeMode(job.getSyncMode());
        if (!MODE_FULL.equals(syncMode)) {
            if ("INCREMENTAL".equals(syncMode)) {
                throw new BusinessException(400, "INCREMENTAL 同步模式暂不支持，当前仅支持 FULL");
            }
            throw new BusinessException(400,
                    "不支持的同步模式: " + job.getSyncMode() + "，当前仅支持 FULL");
        }
    }

    /**
     * 异步执行一条运行记录
     * <p>
     * 由线程池调用。先置 RUNNING 并更新任务状态，再执行真实 JDBC 同步；
     * 无论成功或失败都会回填运行记录与任务状态，不会伪造成功。
     *
     * @param runId 运行记录 ID
     * @param jobId 集成任务 ID
     */
    public void executeRun(Long runId, Long jobId) {
        if (runId == null || jobId == null) {
            log.warn("集成同步执行参数不完整: runId={}, jobId={}", runId, jobId);
            return;
        }
        IntegrationJob job = jobMapper.selectById(jobId);
        if (job == null) {
            finishRun(runId, jobId, IntegrationRun.STATUS_FAILED, 0L, 0L, "集成任务不存在或已删除");
            log.warn("集成同步任务不存在或已删除: jobId={}, runId={}", jobId, runId);
            return;
        }
        if (!startRun(runId, jobId)) {
            // 运行记录已不是 PENDING（可能被重复提交或状态被人工修改），不重复执行
            log.warn("运行记录不是 PENDING，跳过执行: runId={}, jobId={}", runId, jobId);
            return;
        }

        // 在线程内、真实同步前登记 TaskCenter：此时 trigger 事务已提交，且不阻塞 HTTP 请求。
        // start 内部已降级处理，返回 null 表示任务中心不可用，后续 finish 自动跳过。
        Long taskCenterInstanceId = taskCenterClient.start(
                job.getJobCode(), job.getJobName(), runId, resolveRunNo(runId));

        SyncStats stats = new SyncStats();
        try {
            SyncConfig config = parseSyncConfig(job);
            log.info("集成 JDBC 全量同步开始: jobId={}, runId={}, batchSize={}",
                    jobId, runId, config.batchSize());
            executeSync(config, stats);
            finishRun(runId, jobId, IntegrationRun.STATUS_SUCCESS,
                    stats.rowsRead, stats.rowsWritten, null);
            taskCenterClient.finish(taskCenterInstanceId, IntegrationRun.STATUS_SUCCESS,
                    stats.rowsRead, stats.rowsWritten, null);
            log.info("集成 JDBC 全量同步完成: jobId={}, runId={}, rowsRead={}, rowsWritten={}",
                    jobId, runId, stats.rowsRead, stats.rowsWritten);
        } catch (Exception e) {
            String message = buildErrorMessage(e);
            finishRun(runId, jobId, IntegrationRun.STATUS_FAILED,
                    stats.rowsRead, stats.rowsWritten, message);
            taskCenterClient.finish(taskCenterInstanceId, IntegrationRun.STATUS_FAILED,
                    stats.rowsRead, stats.rowsWritten, message);
            log.warn("集成 JDBC 全量同步失败: jobId={}, runId={}, rowsRead={}, rowsWritten={}, error={}",
                    jobId, runId, stats.rowsRead, stats.rowsWritten, message);
        }
    }

    /**
     * 读取运行序号，用于 TaskCenter 标题 {@code jobName #runNo}。
     * <p>
     * 只是观测信息：查询失败记录 warn 并返回 null，由客户端回退使用 runId，
     * 不允许影响集成同步执行。
     *
     * @param runId 运行记录 ID
     * @return 运行序号；读取失败时返回 null
     */
    private Integer resolveRunNo(Long runId) {
        try {
            IntegrationRun run = runMapper.selectById(runId);
            return run == null ? null : run.getRunNo();
        } catch (RuntimeException e) {
            log.warn("读取运行序号失败，TaskCenter 标题回退为 runId: runId={}, error={}",
                    runId, e.toString());
            return null;
        }
    }

    /**
     * 线程池拒绝任务时把 PENDING 运行记录和任务置为 FAILED
     * <p>
     * afterCommit 阶段原事务已提交但仍绑定在线程上，使用 REQUIRES_NEW 确保状态更新
     * 独立提交，不会被已提交事务吞掉。
     *
     * @param runId   运行记录 ID
     * @param jobId   集成任务 ID
     * @param message 失败原因
     */
    public void markFailedBeforeStart(Long runId, Long jobId, String message) {
        String error = truncate(sanitize(message), MAX_ERROR_LENGTH);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<IntegrationRun> runUpdate = new LambdaUpdateWrapper<>();
            runUpdate.eq(IntegrationRun::getId, runId)
                    .in(IntegrationRun::getStatus,
                            IntegrationRun.STATUS_PENDING, IntegrationRun.STATUS_RUNNING)
                    .set(IntegrationRun::getStatus, IntegrationRun.STATUS_FAILED)
                    .set(IntegrationRun::getFinishedAt, now)
                    .set(IntegrationRun::getErrorMessage, error);
            runMapper.update(null, runUpdate);

            LambdaUpdateWrapper<IntegrationJob> jobUpdate = new LambdaUpdateWrapper<>();
            jobUpdate.eq(IntegrationJob::getId, jobId)
                    .set(IntegrationJob::getStatus, IntegrationJob.STATUS_FAILED)
                    .set(IntegrationJob::getLastError, error)
                    .set(IntegrationJob::getUpdatedAt, now)
                    .set(IntegrationJob::getUpdatedBy, "system");
            jobMapper.update(null, jobUpdate);
        });
        log.error("集成同步任务未能提交到线程池: runId={}, jobId={}, error={}",
                runId, jobId, error);
    }

    /**
     * 回收服务崩溃遗留的 PENDING/RUNNING 运行记录。
     * <p>
     * 执行线程是进程内的，服务重启后原线程已不存在；如果不回收，这些记录会永久占用
     * “同一任务禁止并发执行”的约束。超过配置阈值的记录标记 FAILED，并把仍处于
     * RUNNING 的任务置为 FAILED，允许人工重试。
     */
    @Scheduled(fixedDelayString = "${dam.integration.stale-scan-interval-ms:60000}",
            initialDelayString = "${dam.integration.stale-scan-interval-ms:60000}")
    public void recoverStaleRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, staleRunMinutes));
        List<IntegrationRun> stale = runMapper.selectList(
                new LambdaQueryWrapper<IntegrationRun>()
                        .in(IntegrationRun::getStatus,
                                IntegrationRun.STATUS_PENDING, IntegrationRun.STATUS_RUNNING)
                        .lt(IntegrationRun::getCreatedAt, cutoff)
                        .last("LIMIT 100"));
        if (stale.isEmpty()) {
            return;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            for (IntegrationRun run : stale) {
                String message = "执行超时或服务重启，运行记录已回收";
                LambdaUpdateWrapper<IntegrationRun> runUpdate = new LambdaUpdateWrapper<>();
                runUpdate.eq(IntegrationRun::getId, run.getId())
                        .in(IntegrationRun::getStatus,
                                IntegrationRun.STATUS_PENDING, IntegrationRun.STATUS_RUNNING)
                        .set(IntegrationRun::getStatus, IntegrationRun.STATUS_FAILED)
                        .set(IntegrationRun::getFinishedAt, now)
                        .set(IntegrationRun::getErrorMessage, message);
                runMapper.update(null, runUpdate);

                LambdaUpdateWrapper<IntegrationJob> jobUpdate = new LambdaUpdateWrapper<>();
                jobUpdate.eq(IntegrationJob::getId, run.getJobId())
                        .eq(IntegrationJob::getStatus, IntegrationJob.STATUS_RUNNING)
                        .set(IntegrationJob::getStatus, IntegrationJob.STATUS_FAILED)
                        .set(IntegrationJob::getLastError, message)
                        .set(IntegrationJob::getUpdatedAt, now)
                        .set(IntegrationJob::getUpdatedBy, "system");
                jobMapper.update(null, jobUpdate);
            }
        });
        log.warn("回收集成孤儿运行记录 {} 条，阈值 {} 分钟", stale.size(), staleRunMinutes);
    }

    /**
     * 查询指定任务的运行记录（按 run_no 倒序）
     *
     * @param jobId 集成任务 ID
     * @param limit 返回条数，默认 50，最大 200
     * @return 运行记录列表
     */
    public List<IntegrationRun> listRuns(Long jobId, Integer limit) {
        if (jobId == null || jobId <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        if (jobMapper.selectById(jobId) == null) {
            throw new BusinessException(404, "集成任务不存在: " + jobId);
        }
        int size = Math.clamp(limit == null ? DEFAULT_RUN_LIMIT : limit, 1, MAX_RUN_LIMIT);
        LambdaQueryWrapper<IntegrationRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationRun::getJobId, jobId)
                .orderByDesc(IntegrationRun::getRunNo)
                .last("LIMIT " + size);
        return runMapper.selectList(wrapper);
    }

    /**
     * 查询运行记录详情
     *
     * @param runId 运行记录 ID
     * @return 运行记录
     */
    public IntegrationRun getRun(Long runId) {
        if (runId == null || runId <= 0) {
            throw new BusinessException(400, "运行记录ID不合法");
        }
        IntegrationRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(404, "运行记录不存在: " + runId);
        }
        return run;
    }

    // ------------------------------------------------------------------
    //  状态流转
    // ------------------------------------------------------------------

    /**
     * PENDING → RUNNING，同时把任务状态置为 RUNNING 并清理上次错误
     *
     * @return 是否成功切换；运行记录不是 PENDING 时返回 false
     */
    private boolean startRun(Long runId, Long jobId) {
        Boolean started = new TransactionTemplate(transactionManager).execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<IntegrationRun> runUpdate = new LambdaUpdateWrapper<>();
            runUpdate.eq(IntegrationRun::getId, runId)
                    .eq(IntegrationRun::getStatus, IntegrationRun.STATUS_PENDING)
                    .set(IntegrationRun::getStatus, IntegrationRun.STATUS_RUNNING)
                    .set(IntegrationRun::getStartedAt, now)
                    .set(IntegrationRun::getErrorMessage, null);
            if (runMapper.update(null, runUpdate) == 0) {
                return false;
            }

            LambdaUpdateWrapper<IntegrationJob> jobUpdate = new LambdaUpdateWrapper<>();
            jobUpdate.eq(IntegrationJob::getId, jobId)
                    .set(IntegrationJob::getStatus, IntegrationJob.STATUS_RUNNING)
                    .set(IntegrationJob::getLastError, null)
                    .set(IntegrationJob::getLastRunAt, now)
                    .set(IntegrationJob::getUpdatedAt, now)
                    .set(IntegrationJob::getUpdatedBy, "system");
            jobMapper.update(null, jobUpdate);
            return true;
        });
        return Boolean.TRUE.equals(started);
    }

    /**
     * 回填运行终态与任务状态（仅允许从 PENDING/RUNNING 进入终态）
     *
     * @param runId        运行记录 ID
     * @param jobId        集成任务 ID
     * @param status       SUCCESS / FAILED
     * @param rowsRead     读取行数
     * @param rowsWritten  已提交写入行数
     * @param errorMessage 失败原因，成功时为 null
     */
    private void finishRun(Long runId, Long jobId, String status,
                           long rowsRead, long rowsWritten, String errorMessage) {
        String error = IntegrationRun.STATUS_FAILED.equals(status)
                ? truncate(sanitize(errorMessage), MAX_ERROR_LENGTH) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<IntegrationRun> runUpdate = new LambdaUpdateWrapper<>();
            runUpdate.eq(IntegrationRun::getId, runId)
                    .in(IntegrationRun::getStatus,
                            IntegrationRun.STATUS_PENDING, IntegrationRun.STATUS_RUNNING)
                    .set(IntegrationRun::getStatus, status)
                    .set(IntegrationRun::getRowsRead, rowsRead)
                    .set(IntegrationRun::getRowsWritten, rowsWritten)
                    .set(IntegrationRun::getFinishedAt, now)
                    .set(IntegrationRun::getErrorMessage, error);
            if (runMapper.update(null, runUpdate) == 0) {
                log.warn("运行记录已处于终态，跳过完成回填: runId={}, targetStatus={}", runId, status);
            }

            // 只有任务仍处于执行中状态才做 RUNNING → ENABLED/FAILED 流转，
            // 避免覆盖运行期间用户手动切换的 DISABLED 等状态。
            LambdaUpdateWrapper<IntegrationJob> jobUpdate = new LambdaUpdateWrapper<>();
            jobUpdate.eq(IntegrationJob::getId, jobId)
                    .eq(IntegrationJob::getStatus, IntegrationJob.STATUS_RUNNING)
                    .set(IntegrationJob::getStatus,
                            IntegrationRun.STATUS_SUCCESS.equals(status)
                                    ? IntegrationJob.STATUS_ENABLED : IntegrationJob.STATUS_FAILED)
                    .set(IntegrationJob::getLastError,
                            IntegrationRun.STATUS_SUCCESS.equals(status) ? null : error)
                    .set(IntegrationJob::getUpdatedAt, now)
                    .set(IntegrationJob::getUpdatedBy, "system");
            jobMapper.update(null, jobUpdate);
        });
    }

    // ------------------------------------------------------------------
    //  配置解析与校验
    // ------------------------------------------------------------------

    /**
     * 解析并校验源端/目标端配置
     *
     * @param job 集成任务
     * @return 同步配置
     */
    private SyncConfig parseSyncConfig(IntegrationJob job) {
        validateSupported(job);
        Endpoint source = parseEndpoint(job.getSourceConfig(), "sourceConfig", true);
        Endpoint target = parseEndpoint(job.getTargetConfig(), "targetConfig", false);
        int batchSize = source.batchSize() != null
                ? source.batchSize()
                : target.batchSize() != null ? target.batchSize() : DEFAULT_BATCH_SIZE;
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new SyncConfigException("batchSize 必须在 1~" + MAX_BATCH_SIZE + " 之间");
        }
        return new SyncConfig(source, target, batchSize);
    }

    /**
     * 解析单个端点的 JSON 配置（执行/预览使用：要求源端有 query 或 schema+table，目标端必须有 schema+table）
     *
     * @param raw     JSON 字符串
     * @param label   配置字段名（sourceConfig/targetConfig）
     * @param source  是否为源端
     * @return 端点配置
     */
    Endpoint parseEndpoint(String raw, String label, boolean source) {
        return parseEndpoint(raw, label, source, true);
    }

    /**
     * 解析单个端点的 JSON 配置
     * <p>
     * 同一份实现承担所有调用方的校验：URL 仅允许 PostgreSQL，URL/用户名/密码长度受限，
     * 密码使用 CredentialCipher 解密，已提供的 schema/table 必须通过标识符白名单，
     * 源端 query 必须通过只读 SELECT 校验。{@code requireTableOrQuery} 只控制是否强制
     * 要求表结构：连接测试只需要连接信息，可传 false；执行与预览传 true。
     *
     * @param raw                JSON 字符串
     * @param label              配置字段名（sourceConfig/targetConfig）
     * @param source             是否为源端
     * @param requireTableOrQuery 是否要求源端 query 或 schema+table、目标端 schema+table
     * @return 端点配置
     */
    Endpoint parseEndpoint(String raw, String label, boolean source, boolean requireTableOrQuery) {
        if (!hasText(raw)) {
            throw new SyncConfigException(label + " 不能为空");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(raw);
        } catch (RuntimeException e) {
            throw new SyncConfigException(label + " 不是合法的 JSON 对象");
        }
        if (json == null || json.isEmpty()) {
            throw new SyncConfigException(label + " 不是合法的 JSON 对象");
        }

        String url = requireEndpointText(json, "url", label, MAX_URL_LENGTH);
        if (!url.toLowerCase(Locale.ROOT).startsWith(POSTGRESQL_URL_PREFIX)) {
            throw new SyncConfigException(label + ".url 仅支持 PostgreSQL JDBC URL（jdbc:postgresql:...）");
        }
        String username = requireEndpointText(json, "username", label, MAX_USERNAME_LENGTH);
        // 落库密码为 ENC1: 密文时解密；历史明文（无前缀）由组件原样返回
        String password = credentialCipher.decrypt(optionalPassword(json));
        String schema = optionalEndpointText(json, "schema", label, MAX_IDENTIFIER_LENGTH);
        String table = optionalEndpointText(json, "table", label, MAX_IDENTIFIER_LENGTH);
        if (schema != null) {
            validateIdentifier(schema, label + ".schema");
        }
        if (table != null) {
            validateIdentifier(table, label + ".table");
        }
        String query = optionalEndpointText(json, "query", label, MAX_QUERY_LENGTH);
        if (source && query != null) {
            validateSelectQuery(query);
        }
        if (requireTableOrQuery) {
            if (source) {
                if (query == null && (schema == null || table == null)) {
                    throw new SyncConfigException(
                            "sourceConfig 未配置 query 时必须同时配置 schema 和 table");
                }
            } else if (schema == null || table == null) {
                throw new SyncConfigException("targetConfig 必须配置 schema 和 table");
            }
        }
        return new Endpoint(url, username, password, schema, table, query,
                parseBatchSize(json, label));
    }

    /**
     * 校验标识符：只允许 [A-Za-z_][A-Za-z0-9_]*，长度不超过 PostgreSQL 标识符上限
     *
     * @param identifier 标识符
     * @param fieldName  字段名，用于错误提示
     * @return 去除首尾空格后的标识符
     */
    private static String validateIdentifier(String identifier, String fieldName) {
        if (!hasText(identifier)) {
            throw new SyncConfigException(fieldName + " 不能为空");
        }
        String value = identifier.trim();
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new SyncConfigException(fieldName + " 长度不能超过 " + MAX_IDENTIFIER_LENGTH);
        }
        if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new SyncConfigException(fieldName
                    + " 不合法，仅允许字母/数字/下划线且以字母或下划线开头: " + value);
        }
        return value;
    }

    /**
     * 校验 query 仅是一条只读 SELECT
     * <p>
     * 规则：大写归一化后必须以 SELECT 开头；拒绝分号、SQL 注释以及写入/DDL 关键字。
     * 字符串字面量与双引号标识符会先被屏蔽，避免列名/常量中的关键字造成误判。
     *
     * @param query 源端查询
     */
    private static void validateSelectQuery(String query) {
        String normalized = query.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")
                || (upper.length() > "SELECT".length()
                && isIdentifierPart(upper.charAt("SELECT".length())))) {
            throw new SyncConfigException("query 仅允许 SELECT 查询");
        }
        if (normalized.indexOf(';') >= 0) {
            throw new SyncConfigException("query 不允许包含分号或多条语句");
        }
        if (normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new SyncConfigException("query 不允许包含 SQL 注释");
        }
        String masked = maskQuotedSegments(normalized);
        Matcher matcher = FORBIDDEN_QUERY_KEYWORDS.matcher(masked);
        if (matcher.find()) {
            throw new SyncConfigException("query 仅允许只读 SELECT，不允许包含 "
                    + matcher.group(1) + " 等写入/DDL 关键字");
        }
    }

    /**
     * 屏蔽单引号字符串与双引号标识符，便于对 query 做关键字检查
     *
     * @param sql 原始 SQL
     * @return 引号内容替换为空格后的 SQL
     */
    private static String maskQuotedSegments(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '"') {
                    quote = c;
                    masked.append(' ');
                } else {
                    masked.append(c);
                }
            } else {
                if (c == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        // 连续两个引号表示转义，跳过后继续处于引号内
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                masked.append(' ');
            }
        }
        return masked.toString();
    }

    /**
     * 判断字符是否可作为标识符的一部分（用于确认 SELECT 后是空白而不是其他单词）
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ------------------------------------------------------------------
    //  JDBC 同步
    // ------------------------------------------------------------------

    /**
     * 执行真实 JDBC 同步
     * <p>
     * 源连接只读并关闭 autoCommit 以启用游标分批读取；目标连接按批 executeBatch + commit。
     * 任一步骤异常都会向上抛出，由调用方将运行记录置为 FAILED。
     *
     * @param config 同步配置
     * @param stats  行数统计（失败时也能保留已完成部分）
     * @throws SQLException JDBC 执行失败
     */
    private void executeSync(SyncConfig config, SyncStats stats) throws SQLException {
        Endpoint sourceConfig = config.source();
        Endpoint targetConfig = config.target();

        try (Connection sourceConnection = openConnection(sourceConfig, true);
             Connection targetConnection = openConnection(targetConfig, false)) {
            // 源端只读事务 + 游标读取；目标端手动事务按批提交
            sourceConnection.setReadOnly(true);
            sourceConnection.setAutoCommit(false);
            targetConnection.setAutoCommit(false);

            DatabaseMetaData sourceMeta = sourceConnection.getMetaData();
            DatabaseMetaData targetMeta = targetConnection.getMetaData();
            requirePostgres(sourceMeta, "源端");
            requirePostgres(targetMeta, "目标端");

            String sourceSql = buildSourceSql(sourceConfig, sourceMeta);
            TargetTable targetTable = loadTargetTable(targetConnection, targetConfig);

            try (Statement sourceStatement = sourceConnection.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                sourceStatement.setFetchSize(config.batchSize());
                sourceStatement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet resultSet = sourceStatement.executeQuery(sourceSql)) {
                    List<ColumnMapping> mappings =
                            resolveColumns(resultSet.getMetaData(), targetTable);
                    String insertSql = buildInsertSql(targetTable, mappings, targetMeta);
                    try (PreparedStatement targetStatement =
                                 targetConnection.prepareStatement(insertSql)) {
                        targetStatement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        writeBatches(resultSet, targetStatement, targetConnection,
                                mappings, config.batchSize(), stats);
                    }
                }
            }
        }
    }

    /**
     * 构造源端查询：优先使用配置的 query，否则拼装 SELECT * FROM schema.table
     */
    static String buildSourceSql(Endpoint source, DatabaseMetaData metaData)
            throws SQLException {
        if (source.query() != null) {
            return source.query();
        }
        return "SELECT * FROM " + quoteIdentifier(source.schema(), metaData)
                + "." + quoteIdentifier(source.table(), metaData);
    }

    /**
     * 打开 PostgreSQL 连接并设置连接/网络超时
     * <p>
     * 密码只通过 Properties 传给驱动，不进入日志；URL 已限制为 PostgreSQL 前缀。
     */
    private static Connection openConnection(Endpoint endpoint, boolean readOnly)
            throws SQLException {
        return openConnection(endpoint, readOnly, CONNECT_TIMEOUT_SECONDS, SOCKET_TIMEOUT_SECONDS);
    }

    /**
     * 按指定连接/网络超时打开 PostgreSQL 连接（诊断探测用）
     * <p>
     * 与同步执行共用同一套 URL 白名单、凭据解密结果与驱动参数设置，密码只通过
     * Properties 传给驱动，不进入日志。
     *
     * @param endpoint               端点配置
     * @param readOnly               是否只读
     * @param connectTimeoutSeconds  建立连接超时（秒）
     * @param socketTimeoutSeconds   网络读超时（秒）
     * @return 已设置只读属性的连接
     * @throws SQLException 连接失败
     */
    static Connection openConnection(Endpoint endpoint, boolean readOnly,
                                     int connectTimeoutSeconds, int socketTimeoutSeconds)
            throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", endpoint.username());
        properties.setProperty("password", endpoint.password() == null ? "" : endpoint.password());
        properties.setProperty("connectTimeout", String.valueOf(connectTimeoutSeconds));
        properties.setProperty("loginTimeout", String.valueOf(connectTimeoutSeconds));
        properties.setProperty("socketTimeout", String.valueOf(socketTimeoutSeconds));
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("ApplicationName", "dam-integration");
        Connection connection = DriverManager.getConnection(endpoint.url(), properties);
        try {
            connection.setReadOnly(readOnly);
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    /**
     * 确认连接的是 PostgreSQL（驱动只支持 PostgreSQL，这里再做产品名兜底）
     */
    static void requirePostgres(DatabaseMetaData metaData, String side) throws SQLException {
        String product = metaData.getDatabaseProductName();
        if (product == null
                || !product.toLowerCase(Locale.ROOT).contains("postgresql")) {
            throw new SyncConfigException(side + "连接不是 PostgreSQL，当前仅支持 PostgreSQL");
        }
    }

    /**
     * 读取目标表实际列名（列名小写作为匹配键），用于确认源结果集列在目标表中都存在
     */
    private static TargetTable loadTargetTable(Connection connection, Endpoint target)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        TargetTable table = fetchTargetTable(metaData, target.schema(), target.table());
        if (table == null && (!isLowerCase(target.schema()) || !isLowerCase(target.table()))) {
            // PostgreSQL 未加引号的标识符会折叠为小写，这里做一次小写兜底
            table = fetchTargetTable(metaData,
                    target.schema().toLowerCase(Locale.ROOT),
                    target.table().toLowerCase(Locale.ROOT));
        }
        if (table == null) {
            throw new SyncConfigException("目标表不存在或没有可写列: "
                    + target.schema() + "." + target.table());
        }
        return table;
    }

    /**
     * 通过 DatabaseMetaData 读取目标表列；表不存在时返回 null
     */
    private static TargetTable fetchTargetTable(DatabaseMetaData metaData,
                                                 String schema, String table)
            throws SQLException {
        String actualSchema = schema;
        String actualTable = table;
        Map<String, String> columns = new LinkedHashMap<>();
        try (ResultSet columnSet = metaData.getColumns(null, schema, table, null)) {
            while (columnSet.next()) {
                String columnName = columnSet.getString("COLUMN_NAME");
                if (columnName == null) {
                    continue;
                }
                String schemaName = columnSet.getString("TABLE_SCHEM");
                String tableName = columnSet.getString("TABLE_NAME");
                if (schemaName != null) {
                    actualSchema = schemaName;
                }
                if (tableName != null) {
                    actualTable = tableName;
                }
                columns.putIfAbsent(columnName.toLowerCase(Locale.ROOT), columnName);
            }
        }
        if (columns.isEmpty()) {
            return null;
        }
        return new TargetTable(actualSchema, actualTable, columns);
    }

    /**
     * 源结果集列名 → 目标表列名映射；目标表缺列时明确报错
     */
    private static List<ColumnMapping> resolveColumns(ResultSetMetaData metaData,
                                                       TargetTable targetTable)
            throws SQLException {
        int columnCount = metaData.getColumnCount();
        if (columnCount < 1) {
            throw new SyncConfigException("源查询没有返回任何列");
        }
        List<ColumnMapping> mappings = new ArrayList<>(columnCount);
        Set<String> seen = new HashSet<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = metaData.getColumnLabel(i);
            if (!hasText(label)) {
                label = metaData.getColumnName(i);
            }
            if (!hasText(label)) {
                throw new SyncConfigException("源查询第 " + i + " 列没有列名");
            }
            label = validateIdentifier(label, "源查询第 " + i + " 列列名");
            String key = label.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new SyncConfigException("源查询返回重复列名: " + label);
            }
            String targetColumn = targetTable.columns().get(key);
            if (targetColumn == null) {
                throw new SyncConfigException("目标表 " + targetTable.schema() + "."
                        + targetTable.table() + " 缺少列 " + label + "；目标列: "
                        + String.join(", ", targetTable.columns().values()));
            }
            mappings.add(new ColumnMapping(i, targetColumn));
        }
        return mappings;
    }

    /**
     * 构造参数化 INSERT 语句；标识符使用数据库返回的引号包裹
     */
    private static String buildInsertSql(TargetTable targetTable,
                                         List<ColumnMapping> mappings,
                                         DatabaseMetaData metaData) throws SQLException {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < mappings.size(); i++) {
            if (i > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(quoteIdentifier(mappings.get(i).targetColumn(), metaData));
            placeholders.append('?');
        }
        return "INSERT INTO " + quoteIdentifier(targetTable.schema(), metaData)
                + "." + quoteIdentifier(targetTable.table(), metaData)
                + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    /**
     * 逐行读取源结果集并批量写入目标表；每批 executeBatch 后提交
     */
    private static void writeBatches(ResultSet resultSet, PreparedStatement targetStatement,
                                     Connection targetConnection, List<ColumnMapping> mappings,
                                     int batchSize, SyncStats stats) throws SQLException {
        int pending = 0;
        try {
            while (resultSet.next()) {
                stats.rowsRead++;
                for (ColumnMapping mapping : mappings) {
                    Object value = resultSet.getObject(mapping.sourceIndex());
                    targetStatement.setObject(mapping.sourceIndex(), value);
                }
                targetStatement.addBatch();
                pending++;
                if (pending >= batchSize) {
                    flushBatch(targetStatement, targetConnection);
                    stats.rowsWritten += pending;
                    pending = 0;
                }
            }
            if (pending > 0) {
                flushBatch(targetStatement, targetConnection);
                stats.rowsWritten += pending;
            }
        } catch (SQLException e) {
            // 只回滚当前未提交批次；此前已提交批次保留（追加写入，不做删除）
            try {
                targetConnection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }

    /**
     * 执行一个批次并提交，确保 rows_written 只统计已提交数据
     */
    private static void flushBatch(PreparedStatement statement, Connection connection)
            throws SQLException {
        int[] counts = statement.executeBatch();
        for (int count : counts) {
            if (count == Statement.EXECUTE_FAILED) {
                throw new SQLException("目标端批量写入存在失败批次");
            }
        }
        connection.commit();
        statement.clearBatch();
    }

    /**
     * 使用数据库标识符引号包裹已通过白名单校验的标识符
     * <p>
     * PostgreSQL 返回的 quote 为双引号；若驱动表示不支持引号（返回空格），
     * 因标识符已严格白名单校验，直接使用裸标识符。
     */
    private static String quoteIdentifier(String identifier, DatabaseMetaData metaData)
            throws SQLException {
        String quote = metaData.getIdentifierQuoteString();
        if (quote == null || quote.isBlank() || " ".equals(quote)) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    // ------------------------------------------------------------------
    //  通用工具
    // ------------------------------------------------------------------

    /**
     * 读取必填文本字段
     */
    private static String requireEndpointText(JSONObject json, String key,
                                              String label, int maxLength) {
        String value = json.getStr(key);
        if (!hasText(value)) {
            throw new SyncConfigException(label + "." + key + " 不能为空");
        }
        value = value.trim();
        if (value.length() > maxLength) {
            throw new SyncConfigException(label + "." + key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    /**
     * 读取可选文本字段，空白转为 null
     */
    private static String optionalEndpointText(JSONObject json, String key,
                                               String label, int maxLength) {
        String value = json.getStr(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new SyncConfigException(label + "." + key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    /**
     * 读取密码；允许为空，但绝不写入日志或错误信息
     */
    private static String optionalPassword(JSONObject json) {
        Object value = json.get("password");
        if (value == null) {
            return "";
        }
        String password = String.valueOf(value);
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new SyncConfigException("password 长度不能超过 " + MAX_PASSWORD_LENGTH);
        }
        return password;
    }

    /**
     * 读取并校验 batchSize（可选，1~1000）
     */
    private static Integer parseBatchSize(JSONObject json, String label) {
        Object raw = json.get("batchSize");
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new SyncConfigException(label + ".batchSize 必须是整数");
        }
        if (value < 1 || value > MAX_BATCH_SIZE) {
            throw new SyncConfigException(label + ".batchSize 必须在 1~"
                    + MAX_BATCH_SIZE + " 之间");
        }
        return value;
    }

    /**
     * 归一化数据源类型
     */
    static String normalizeType(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 归一化同步模式；未配置时按 FULL 处理
     */
    private static String normalizeMode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? MODE_FULL : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 构造日志/落库用的错误信息：取异常链中第一个非空 message，脱敏后截断到 2000 字符
     */
    static String buildErrorMessage(Throwable error) {
        String message = null;
        Throwable current = error;
        while (current != null && !hasText(message)) {
            message = current.getMessage();
            current = current.getCause();
        }
        if (!hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        if (error instanceof SQLException sqlError && hasText(sqlError.getSQLState())
                && !message.contains(sqlError.getSQLState())) {
            message = message + " (SQLState=" + sqlError.getSQLState() + ")";
        }
        return truncate(sanitize(message), MAX_ERROR_LENGTH);
    }

    /**
     * 脱敏：移除连接串/错误信息中的 password、pwd 参数值及 URL userinfo 中的密码
     */
    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("(?i)(password\\s*[=:]\\s*)([^&;\\s,]+)", "$1***")
                .replaceAll("(?i)(pwd\\s*[=:]\\s*)([^&;\\s,]+)", "$1***")
                .replaceAll("(?i)(//[^/@\\s:]+:)([^@/\\s]+)(@)", "$1***$3");
    }

    /**
     * 截断文本
     */
    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    /**
     * 判断字符串是否已经是小写
     */
    private static boolean isLowerCase(String value) {
        return value.equals(value.toLowerCase(Locale.ROOT));
    }

    /**
     * 去除首尾空格，空字符串转为 null
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断文本是否包含非空白字符
     */
    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 同步配置
     */
    private record SyncConfig(Endpoint source, Endpoint target, int batchSize) {
    }

    /**
     * 单个端点的 JDBC 配置
     */
    record Endpoint(String url, String username, String password, String schema,
                    String table, String query, Integer batchSize) {

        /**
         * 防御性重写：即使未来有人打印配置对象，也不会把密码写进日志
         */
        @Override
        public String toString() {
            return "Endpoint[url=" + url + ", username=" + username
                    + ", password=***, schema=" + schema + ", table=" + table
                    + ", query=" + query + ", batchSize=" + batchSize + "]";
        }
    }

    /**
     * 目标表元数据：实际 schema/table 名与“小写列名 → 实际列名”映射
     */
    private record TargetTable(String schema, String table, Map<String, String> columns) {
    }

    /**
     * 源结果集列序号与目标列名映射
     */
    private record ColumnMapping(int sourceIndex, String targetColumn) {
    }

    /**
     * 可变行数统计，异常时仍可读取已完成部分
     */
    private static final class SyncStats {
        private long rowsRead;
        private long rowsWritten;
    }

    /**
     * 配置/查询校验失败异常
     */
    private static final class SyncConfigException extends RuntimeException {
        private SyncConfigException(String message) {
            super(message);
        }
    }
}
