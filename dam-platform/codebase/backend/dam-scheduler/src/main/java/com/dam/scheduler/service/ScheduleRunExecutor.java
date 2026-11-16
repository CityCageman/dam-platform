package com.dam.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dam.common.security.CredentialCipher;
import com.dam.scheduler.client.TaskCenterClient;
import com.dam.scheduler.entity.ScheduleDag;
import com.dam.scheduler.entity.ScheduleJob;
import com.dam.scheduler.entity.ScheduleRun;
import com.dam.scheduler.repository.ScheduleDagMapper;
import com.dam.scheduler.repository.ScheduleJobMapper;
import com.dam.scheduler.repository.ScheduleRunMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基础顺序 SQL DAG 执行器。
 * <p>
 * 运行语义：
 * <ul>
 *   <li>手动触发与 Cron 派发在事务提交后提交执行，保证工作线程能读到 PENDING 记录；</li>
 *   <li>run 状态严格按 PENDING → RUNNING → SUCCESS/FAILED 流转，不伪造成功；</li>
 *   <li>按 definition.nodes 顺序逐个 JDBC 执行单条只读 SELECT/WITH；节点失败立即停止，
 *       错误包含节点 code；每节点最多消费 100 行，不保存结果集；</li>
 *   <li>线程池 core=2/max=4/queue=100，拒绝时 PENDING 直接置 FAILED；</li>
 *   <li>TaskCenter 回调统一归口在本执行器：开始 DAG 前登记 start 并拿到 instanceId，
 *       执行结束回写 SUCCESS/FAILED；回调失败只 warn，不改变本地运行状态；</li>
 *   <li>连接、查询设有超时，连接强制只读事务；密码只用于 JDBC 属性，日志与
 *       error_message 均做脱敏；</li>
 *   <li>定时回收超过 180 分钟的 RUNNING 记录（服务重启遗留），标记 FAILED。</li>
 * </ul>
 * 不支持并行分支、条件分支与重试编排。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
public class ScheduleRunExecutor {

    /** 运行状态：待执行 */
    private static final String STATUS_PENDING = "PENDING";

    /** 运行状态：执行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 运行状态：成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 运行状态：失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 线程池核心线程数 */
    private static final int POOL_CORE_SIZE = 2;

    /** 线程池最大线程数 */
    private static final int POOL_MAX_SIZE = 4;

    /** 线程池有界队列容量 */
    private static final int POOL_QUEUE_CAPACITY = 100;

    /** 空闲线程存活时间（秒） */
    private static final long POOL_KEEP_ALIVE_SECONDS = 60L;

    /** 建连超时（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** 单节点查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /** socket 读超时余量（秒），避免查询取消后网络读取无限阻塞 */
    private static final int SOCKET_TIMEOUT_MARGIN_SECONDS = 10;

    /** 单节点最多消费行数 */
    private static final int MAX_ROWS_PER_NODE = 100;

    /** 错误信息最大长度 */
    private static final int MAX_ERROR_LENGTH = 2000;

    /** 回收扫描单批最大条数 */
    private static final int MAX_STALE_BATCH = 100;

    /** 运行记录 Mapper */
    private final ScheduleRunMapper runMapper;

    /** 调度任务 Mapper */
    private final ScheduleJobMapper jobMapper;

    /** DAG 定义 Mapper */
    private final ScheduleDagMapper dagMapper;

    /** 凭据加解密组件，执行节点前解密 datasource.password */
    private final CredentialCipher credentialCipher;

    /** TaskCenter 回调客户端，统一负责运行开始/结束回写 */
    private final TaskCenterClient taskCenterClient;

    /** 执行线程池 */
    private final ThreadPoolExecutor executor;

    /** 线程池拒绝时使用独立事务回填 FAILED */
    private final TransactionTemplate requiresNewTemplate;

    /** 遗留 RUNNING 判定阈值（分钟） */
    private final long staleRunMinutes;

    /**
     * 构造执行器。
     *
     * @param runMapper          运行记录 Mapper
     * @param jobMapper          调度任务 Mapper
     * @param dagMapper          DAG 定义 Mapper
     * @param credentialCipher   凭据加解密组件
     * @param taskCenterClient   TaskCenter 回调客户端
     * @param transactionManager 平台事务管理器
     * @param staleRunMinutes    遗留 RUNNING 阈值，默认 180 分钟
     */
    public ScheduleRunExecutor(ScheduleRunMapper runMapper,
                               ScheduleJobMapper jobMapper,
                               ScheduleDagMapper dagMapper,
                               CredentialCipher credentialCipher,
                               TaskCenterClient taskCenterClient,
                               PlatformTransactionManager transactionManager,
                               @Value("${dam.scheduler.stale-run-minutes:180}") long staleRunMinutes) {
        this.runMapper = runMapper;
        this.jobMapper = jobMapper;
        this.dagMapper = dagMapper;
        this.credentialCipher = credentialCipher;
        this.taskCenterClient = taskCenterClient;
        this.staleRunMinutes = Math.max(1, staleRunMinutes);
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        AtomicInteger threadSeq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                POOL_CORE_SIZE,
                POOL_MAX_SIZE,
                POOL_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(POOL_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "schedule-dag-executor-" + threadSeq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 在当前事务提交后提交执行；没有事务时立即提交。
     * <p>
     * 触发/派发方法在事务内调用本方法，避免工作线程先于运行记录提交而读不到 PENDING。
     * 事务回滚时 afterCommit 不触发，不会产生孤儿执行。提交线程（手动触发的请求线程或
     * Cron 派发线程）在此捕获 Sa-Token，随后由执行线程显式透传给 TaskCenter 回调。
     *
     * @param runId 运行记录ID
     * @param jobId 调度任务ID
     */
    public void submitAfterCommit(Long runId, Long jobId) {
        if (runId == null || jobId == null) {
            log.warn("提交 DAG 执行参数不完整: runId={}, jobId={}", runId, jobId);
            return;
        }
        String token = TaskCenterClient.captureToken();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(runId, jobId, token);
                }
            });
        } else {
            submit(runId, jobId, token);
        }
    }

    /**
     * 提交到有界线程池；拒绝时把运行记录置为 FAILED。
     * <p>
     * 被拒绝的运行没有进入执行线程，也就没有登记 TaskCenter、拿不到 instanceId，
     * 因此只回填本地 FAILED，不需要（也无法）调用 finish。
     *
     * @param runId 运行记录ID
     * @param jobId 调度任务ID
     * @param token 提交线程捕获的 Sa-Token，可为空
     */
    private void submit(Long runId, Long jobId, String token) {
        try {
            executor.execute(() -> executeRun(runId, jobId, token));
        } catch (RejectedExecutionException e) {
            String error = "DAG 执行线程池已满，运行未执行";
            markFailedBeforeStart(runId, error);
            log.error("DAG 执行线程池拒绝任务: runId={}, jobId={}", runId, jobId);
        }
    }

    /**
     * 异步执行一条运行记录。
     * <p>
     * 该方法只在线程池线程中运行。拿到 ScheduleJob 后先向 TaskCenter 登记开始，
     * 随后无论 DAG 成功、失败还是异常终止，都在 finally 中回写对应终态；
     * 登记失败时 instanceId 为 null，finish 自动跳过。本地运行状态不受回调影响。
     * 任何异常都会尽力回填 FAILED，且日志/error_message 只使用脱敏后的消息，
     * 不输出 DAG 定义或密码。
     *
     * @param runId 运行记录ID
     * @param jobId 调度任务ID
     * @param token 提交线程捕获的 Sa-Token，可为空
     */
    private void executeRun(Long runId, Long jobId, String token) {
        List<ScheduleDagValidator.SqlNode> nodes = List.of();
        ScheduleJob job = null;
        try {
            ScheduleRun run = runMapper.selectById(runId);
            if (run == null) {
                log.warn("DAG 执行跳过：运行记录不存在: runId={}", runId);
                return;
            }
            if (!startRun(runId)) {
                log.warn("DAG 执行跳过：运行记录不是 PENDING: runId={}, jobId={}", runId, jobId);
                return;
            }

            job = jobMapper.selectById(jobId);
            if (job == null) {
                failRun(runId, "调度任务不存在或已删除: " + jobId);
                log.warn("DAG 执行失败：调度任务不存在或已删除: runId={}, jobId={}", runId, jobId);
                return;
            }
        } catch (Exception e) {
            String message = "执行器异常: " + sanitize(e, nodes);
            failRun(runId, message);
            log.error("DAG 执行器异常: runId={}, jobId={}, errorClass={}, error={}",
                    runId, jobId, e.getClass().getSimpleName(), message);
            return;
        }

        // 开始 DAG 前统一登记 TaskCenter；失败返回 null，本地继续执行且不影响终态
        Long instanceId = taskCenterClient.start(
                job.getJobCode(), job.getJobName(), runId, token);
        String status = STATUS_FAILED;
        String error = "DAG 执行未完成";
        try {
            String dagCode = trimToNull(job.getDagCode());
            if (dagCode == null) {
                error = "调度任务未关联 DAG（job.dagCode 为空）";
                failRun(runId, error);
                log.warn("DAG 执行失败：任务未关联 DAG: runId={}, jobId={}, jobCode={}",
                        runId, jobId, job.getJobCode());
                return;
            }

            ScheduleDag dag = dagMapper.selectById(dagCode);
            if (dag == null) {
                error = "关联的 DAG 不存在: " + dagCode;
                failRun(runId, error);
                log.warn("DAG 执行失败：关联 DAG 不存在: runId={}, jobId={}, dagCode={}",
                        runId, jobId, dagCode);
                return;
            }

            try {
                nodes = ScheduleDagValidator.readNodes(dag.getDefinition());
            } catch (Exception e) {
                error = "DAG 定义不合法: " + sanitize(e, List.of());
                failRun(runId, error);
                log.warn("DAG 执行失败：定义不合法: runId={}, jobId={}, dagCode={}, error={}",
                        runId, jobId, dagCode, error);
                return;
            }

            // 执行前解密：ENC1: 密文还原为明文，历史明文由 CredentialCipher 原样返回
            nodes = decryptPasswords(nodes);

            log.info("DAG 顺序执行开始: runId={}, jobId={}, jobCode={}, dagCode={}, nodeCount={}",
                    runId, jobId, job.getJobCode(), dagCode, nodes.size());
            for (ScheduleDagValidator.SqlNode node : nodes) {
                try {
                    int rows = executeNode(node);
                    log.info("DAG 节点执行成功: runId={}, dagCode={}, nodeCode={}, consumedRows={}",
                            runId, dagCode, node.code(), rows);
                } catch (Exception e) {
                    error = "节点[" + node.code() + "]执行失败: "
                            + sanitize(e, List.of(node));
                    failRun(runId, error);
                    log.warn("DAG 节点执行失败: runId={}, jobId={}, dagCode={}, nodeCode={}, error={}",
                            runId, jobId, dagCode, node.code(), error);
                    return;
                }
            }

            finishRun(runId, STATUS_SUCCESS, null);
            status = STATUS_SUCCESS;
            error = null;
            log.info("DAG 顺序执行成功: runId={}, jobId={}, dagCode={}, nodeCount={}",
                    runId, jobId, dagCode, nodes.size());
        } catch (Exception e) {
            error = "执行器异常: " + sanitize(e, nodes);
            failRun(runId, error);
            log.error("DAG 执行器异常: runId={}, jobId={}, errorClass={}, error={}",
                    runId, jobId, e.getClass().getSimpleName(), error);
        } finally {
            taskCenterClient.finish(instanceId, status, error, token);
        }
    }

    /**
     * 解密节点密码，生成仅供本次执行使用的节点副本。
     * <p>
     * {@code ENC1:} 密文由 {@link CredentialCipher#decrypt(String)} 还原为明文；
     * 历史明文和解密失败的值按明文兼容，避免存量 DAG 无法执行。库内定义不被改写，
     * 解密后的明文只用于 JDBC 连接，日志与 error_message 仍由脱敏逻辑处理。
     *
     * @param nodes 库内定义解析出的节点
     * @return 密码已解密的节点列表
     */
    private List<ScheduleDagValidator.SqlNode> decryptPasswords(
            List<ScheduleDagValidator.SqlNode> nodes) {
        return nodes.stream()
                .map(node -> new ScheduleDagValidator.SqlNode(node.code(), node.name(),
                        node.url(), node.username(), credentialCipher.decrypt(node.password()),
                        node.sql()))
                .toList();
    }

    /**
     * 执行单个 SQL 节点：只读事务、查询超时、最多消费 100 行。
     * <p>
     * 每个节点独立建连，支持节点各自 datasource；节点之间不传递参数，不保存结果。
     *
     * @param node SQL 节点
     * @return 实际消费行数（0-100）
     * @throws SQLException 建连/执行/超时失败
     */
    private int executeNode(ScheduleDagValidator.SqlNode node) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", node.username());
        // 密码只作为 JDBC 属性使用，绝不拼入 URL/日志/错误消息
        properties.setProperty("password", node.password());
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        properties.setProperty("socketTimeout", String.valueOf(
                (QUERY_TIMEOUT_SECONDS + SOCKET_TIMEOUT_MARGIN_SECONDS) * 1000L));
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("ApplicationName", "dam-scheduler-dag");

        try (Connection connection = DriverManager.getConnection(node.url(), properties)) {
            requirePostgres(connection);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement transaction = connection.createStatement()) {
                transaction.setQueryTimeout(Math.min(QUERY_TIMEOUT_SECONDS, 10));
                // 显式只读事务：即使 SQL 校验被绕过，服务端也会拒绝写操作
                transaction.execute("SET TRANSACTION READ ONLY");
            }

            int rows = 0;
            try (Statement statement = connection.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(MAX_ROWS_PER_NODE);
                statement.setFetchSize(MAX_ROWS_PER_NODE);
                try (ResultSet resultSet = statement.executeQuery(node.sql())) {
                    // 只消费不返回；即使驱动忽略 maxRows，循环也严格限制在 100 行
                    while (rows < MAX_ROWS_PER_NODE && resultSet.next()) {
                        rows++;
                    }
                }
            }
            quietRollback(connection);
            return rows;
        }
    }

    /**
     * 确认连接目标是 PostgreSQL。
     *
     * @param connection JDBC 连接
     * @throws SQLException 非 PostgreSQL 时抛出
     */
    private static void requirePostgres(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String product = metaData == null ? null : metaData.getDatabaseProductName();
        if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgresql")) {
            throw new SQLException("目标数据库不是 PostgreSQL，已拒绝执行");
        }
    }

    /**
     * 回滚只读事务；失败仅 debug（连接即将关闭）。
     *
     * @param connection JDBC 连接
     */
    private static void quietRollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.debug("回滚 DAG 只读事务失败（连接即将关闭）: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * PENDING → RUNNING，并记录 started_at。
     *
     * @param runId 运行记录ID
     * @return 是否切换成功；非 PENDING 时返回 false
     */
    private boolean startRun(Long runId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ScheduleRun> update = new LambdaUpdateWrapper<ScheduleRun>()
                .eq(ScheduleRun::getId, runId)
                .eq(ScheduleRun::getRunStatus, STATUS_PENDING)
                .set(ScheduleRun::getRunStatus, STATUS_RUNNING)
                .set(ScheduleRun::getStartedAt, now)
                .set(ScheduleRun::getFinishedAt, null)
                .set(ScheduleRun::getErrorMessage, null);
        return runMapper.update(null, update) == 1;
    }

    /**
     * RUNNING → SUCCESS/FAILED，并记录 finished_at（失败时写入脱敏错误）。
     *
     * @param runId  运行记录ID
     * @param status SUCCESS/FAILED
     * @param error  失败原因，成功时为 null
     */
    private void finishRun(Long runId, String status, String error) {
        String safeError = STATUS_FAILED.equals(status) ? truncate(error) : null;
        LambdaUpdateWrapper<ScheduleRun> update = new LambdaUpdateWrapper<ScheduleRun>()
                .eq(ScheduleRun::getId, runId)
                .eq(ScheduleRun::getRunStatus, STATUS_RUNNING)
                .set(ScheduleRun::getRunStatus, status)
                .set(ScheduleRun::getFinishedAt, LocalDateTime.now())
                .set(ScheduleRun::getErrorMessage, safeError);
        if (runMapper.update(null, update) == 0) {
            log.warn("运行记录已不处于 RUNNING，跳过终态回填: runId={}, targetStatus={}",
                    runId, status);
        }
    }

    /**
     * 安全回填 FAILED：允许从 PENDING/RUNNING 进入；更新失败只记录脱敏日志。
     *
     * @param runId 运行记录ID
     * @param error 已脱敏错误消息
     */
    private void failRun(Long runId, String error) {
        String safeError = truncate(error);
        try {
            int updated = runMapper.update(null, new LambdaUpdateWrapper<ScheduleRun>()
                    .eq(ScheduleRun::getId, runId)
                    .in(ScheduleRun::getRunStatus, STATUS_PENDING, STATUS_RUNNING)
                    .set(ScheduleRun::getRunStatus, STATUS_FAILED)
                    .set(ScheduleRun::getFinishedAt, LocalDateTime.now())
                    .set(ScheduleRun::getErrorMessage, safeError));
            if (updated == 0) {
                log.warn("运行记录已处于终态，跳过 FAILED 回填: runId={}", runId);
            }
        } catch (Exception e) {
            log.error("回填运行失败状态异常: runId={}, errorClass={}",
                    runId, e.getClass().getSimpleName());
        }
    }

    /**
     * 线程池拒绝时独立事务回填 FAILED，避免 afterCommit 阶段被已提交事务吞掉。
     *
     * @param runId 运行记录ID
     * @param error 失败原因
     */
    private void markFailedBeforeStart(Long runId, String error) {
        String safeError = truncate(error);
        try {
            requiresNewTemplate.executeWithoutResult(status -> runMapper.update(null,
                    new LambdaUpdateWrapper<ScheduleRun>()
                            .eq(ScheduleRun::getId, runId)
                            .eq(ScheduleRun::getRunStatus, STATUS_PENDING)
                            .set(ScheduleRun::getRunStatus, STATUS_FAILED)
                            .set(ScheduleRun::getFinishedAt, LocalDateTime.now())
                            .set(ScheduleRun::getErrorMessage, safeError)));
        } catch (Exception e) {
            log.error("线程池拒绝后回填 FAILED 异常: runId={}, errorClass={}",
                    runId, e.getClass().getSimpleName());
        }
    }

    /**
     * 回收服务崩溃/重启遗留的超时 RUNNING 记录。
     * <p>
     * 执行线程是进程内的，服务重启后原线程已不存在；超过阈值仍为 RUNNING 的记录
     * 标记 FAILED，避免永久卡在执行中。默认阈值 180 分钟，可用
     * {@code dam.scheduler.stale-run-minutes} 调整。
     */
    @Scheduled(fixedDelayString = "${dam.scheduler.stale-run-scan-interval-ms:60000}",
            initialDelayString = "${dam.scheduler.stale-run-scan-interval-ms:60000}")
    public void recoverStaleRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleRunMinutes);
        List<ScheduleRun> stale;
        try {
            stale = runMapper.selectList(new LambdaQueryWrapper<ScheduleRun>()
                    .eq(ScheduleRun::getRunStatus, STATUS_RUNNING)
                    .and(w -> w.lt(ScheduleRun::getStartedAt, cutoff)
                            .or()
                            .isNull(ScheduleRun::getStartedAt)
                            .lt(ScheduleRun::getCreatedAt, cutoff))
                    .last("LIMIT " + MAX_STALE_BATCH));
        } catch (Exception e) {
            log.error("扫描遗留 RUNNING 运行记录异常: errorClass={}", e.getClass().getSimpleName());
            return;
        }
        if (stale.isEmpty()) {
            return;
        }

        int recovered = 0;
        LocalDateTime now = LocalDateTime.now();
        String message = "运行超过 " + staleRunMinutes
                + " 分钟仍为 RUNNING，可能因服务重启或执行线程丢失，已自动标记 FAILED";
        for (ScheduleRun run : stale) {
            try {
                int updated = runMapper.update(null, new LambdaUpdateWrapper<ScheduleRun>()
                        .eq(ScheduleRun::getId, run.getId())
                        .eq(ScheduleRun::getRunStatus, STATUS_RUNNING)
                        .set(ScheduleRun::getRunStatus, STATUS_FAILED)
                        .set(ScheduleRun::getFinishedAt, now)
                        .set(ScheduleRun::getErrorMessage, message));
                if (updated > 0) {
                    recovered++;
                }
            } catch (Exception e) {
                log.warn("回收遗留 RUNNING 记录失败: runId={}, errorClass={}",
                        run.getId(), e.getClass().getSimpleName());
            }
        }
        if (recovered > 0) {
            log.warn("回收遗留 RUNNING 运行记录 {} 条，阈值 {} 分钟", recovered, staleRunMinutes);
        }
    }

    /**
     * 异常消息脱敏：替换所有节点密码、清理换行并限制长度。
     *
     * @param error 异常
     * @param nodes 已读取的节点列表，可为空
     * @return 可落库/可日志的消息
     */
    private static String sanitize(Throwable error, List<ScheduleDagValidator.SqlNode> nodes) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        for (ScheduleDagValidator.SqlNode node : nodes) {
            String password = node.password();
            if (password != null && !password.isEmpty()) {
                message = message.replace(password, "***");
            }
        }
        // 兜底处理 password=xxx 形式的连接参数回显
        message = message.replaceAll("(?i)password\\s*=\\s*[^\\s,;&]+", "password=***");
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH) + "..." : message;
    }

    /**
     * 截断错误消息。
     *
     * @param message 原始消息
     * @return 不超过 2000 字符的消息
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH) + "..." : message;
    }

    /**
     * 裁剪并归一化空白文本。
     *
     * @param raw 原始值
     * @return 裁剪后的文本或 null
     */
    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 关闭执行线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
