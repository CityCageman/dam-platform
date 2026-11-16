package com.dam.scheduler.client;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskCenter 回调客户端。
 * <p>
 * 调度服务在手动触发与 Cron 自动派发写入 PENDING 运行记录后，调用
 * {@code POST /api/task-center/callbacks/start} 把本次运行登记到统一任务中心。
 * 只登记开始（TaskCenter 侧实例状态为 PENDING），不调用 finish：
 * 本服务没有真实 DAG 执行器，不能伪造执行成功。
 * <p>
 * 调度可用性优先：连接/读取超时设置得较短，调用失败只记录 warn，不向上抛出；
 * TaskCenter 不可用不会阻塞或回滚调度派发。Cron 派发使用 {@link #registerStartAsync}
 * 在事务提交后异步登记，避免慢调用拖慢扫描。
 * <p>
 * 鉴权沿用调用方 Sa-Token：优先读取当前请求的 {@code token} 请求头，
 * 定时任务线程没有请求上下文时退化为无 token 的内部调用。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class TaskCenterClient {

    /** 来源服务标识，与 TaskCenter 任务定义的 sourceService 对应 */
    private static final String SOURCE_SERVICE = "dam-scheduler";

    /** 任务类型 */
    private static final String TASK_TYPE = "SCHEDULER";

    /** 回调开始接口路径 */
    private static final String START_PATH = "/api/task-center/callbacks/start";

    /** TaskCenter title 字段最大长度 */
    private static final int MAX_TITLE_LENGTH = 200;

    /** 异步登记队列容量，满时直接丢弃并 warn，避免阻塞调度线程 */
    private static final int REGISTRATION_QUEUE_CAPACITY = 512;

    /** 登记线程池 */
    private final ExecutorService registrationExecutor;

    /** TaskCenter HTTP 客户端 */
    private final RestClient restClient;

    /**
     * 构造 TaskCenter 客户端。
     *
     * @param taskCenterUrl TaskCenter 基础地址，默认本机 8099
     */
    public TaskCenterClient(
            @Value("${dam.task-center-url:http://127.0.0.1:8099}") String taskCenterUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        // 读超时略大于连接超时，覆盖 TaskCenter 正常登记耗时；超时后只 warn
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(taskCenterUrl)
                .requestFactory(requestFactory)
                .build();

        AtomicInteger threadSeq = new AtomicInteger();
        this.registrationExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(REGISTRATION_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "task-center-registrar-" + threadSeq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 同步登记一次运行实例（手动触发路径）。
     * <p>
     * 调用失败会抛出运行时异常，由调用方转换为 warn 日志，不影响本地触发结果。
     *
     * @param jobCode 调度任务编码，作为 sourceTaskCode
     * @param jobName 调度任务名称，用于拼接 title
     * @param runId   本地 schedule_run 主键，作为 sourceRunId
     */
    public void registerStart(String jobCode, String jobName, Long runId) {
        registerStart(jobCode, jobName, runId, currentToken());
    }

    /**
     * 异步登记一次运行实例（Cron 自动派发路径）。
     * <p>
     * 在调用线程捕获 Sa-Token（事务提交回调仍处于请求线程），再交给单线程池执行，
     * 避免 TaskCenter 慢调用拖慢派发扫描；任何异常只记录 warn。
     *
     * @param jobCode 调度任务编码，作为 sourceTaskCode
     * @param jobName 调度任务名称，用于拼接 title
     * @param runId   本地 schedule_run 主键，作为 sourceRunId
     */
    public void registerStartAsync(String jobCode, String jobName, Long runId) {
        String token = currentToken();
        try {
            registrationExecutor.execute(() -> {
                try {
                    registerStart(jobCode, jobName, runId, token);
                } catch (Exception e) {
                    log.warn("TaskCenter 异步登记失败，不影响调度派发: jobCode={}, runId={}, error={}",
                            jobCode, runId, e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("TaskCenter 登记队列已满，跳过本次登记，不影响调度派发: jobCode={}, runId={}",
                    jobCode, runId);
        }
    }

    /**
     * 执行回调开始请求。
     *
     * @param jobCode 调度任务编码
     * @param jobName 调度任务名称
     * @param runId   本地运行记录ID
     * @param token   透传的 Sa-Token，可为空
     */
    @SuppressWarnings("unchecked")
    private void registerStart(String jobCode, String jobName, Long runId, String token) {
        if (!StringUtils.hasText(jobCode) || runId == null) {
            throw new IllegalArgumentException("登记 TaskCenter 需要 jobCode 与 runId");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceService", SOURCE_SERVICE);
        body.put("sourceTaskCode", jobCode);
        body.put("sourceRunId", String.valueOf(runId));
        body.put("taskType", TASK_TYPE);
        body.put("title", buildTitle(jobName, runId));
        // 任务定义缺失时由 TaskCenter 自动登记 ENABLED 定义并继续创建实例
        body.put("autoCreateDefinition", true);

        Map<String, Object> response = restClient.post()
                .uri(START_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(token)) {
                        headers.set("token", token);
                    }
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        int code = response != null && response.get("code") instanceof Number number
                ? number.intValue() : 0;
        if (code != 200) {
            throw new IllegalStateException("TaskCenter start 返回失败: code=" + code
                    + ", message=" + (response == null ? null : response.get("message")));
        }

        Long instanceId = extractInstanceId(response);
        if (instanceId == null) {
            throw new IllegalStateException("TaskCenter start 响应缺少 instanceId: " + response);
        }
        log.info("TaskCenter 运行登记成功: jobCode={}, runId={}, instanceId={}",
                jobCode, runId, instanceId);
    }

    /**
     * 从统一 Result 响应中读取 data.instanceId。
     *
     * @param response TaskCenter 响应
     * @return 实例ID，缺失时为 null
     */
    private static Long extractInstanceId(Map<String, Object> response) {
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }
        Object raw = dataMap.get("instanceId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null) {
            try {
                return Long.valueOf(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 拼接 {@code jobName + " run " + runId}，超出 TaskCenter title 上限时截断任务名。
     *
     * @param jobName 任务名称
     * @param runId   运行记录ID
     * @return 不超过 200 字符的标题
     */
    private static String buildTitle(String jobName, Long runId) {
        String suffix = " run " + runId;
        String name = jobName == null ? "" : jobName.trim();
        int maxNameLength = Math.max(0, MAX_TITLE_LENGTH - suffix.length());
        if (name.length() > maxNameLength) {
            name = name.substring(0, maxNameLength);
        }
        return name + suffix;
    }

    /**
     * 读取当前请求的 token 请求头；无请求上下文或无登录态时返回 null。
     *
     * @return Sa-Token 值，可为 null
     */
    private static String currentToken() {
        try {
            String token = SaHolder.getRequest().getHeader("token");
            if (StringUtils.hasText(token)) {
                return token.trim();
            }
        } catch (Exception ignored) {
            // 定时派发线程没有 Sa-Token 请求上下文，继续尝试无 token 内部调用
        }
        try {
            String token = StpUtil.getTokenValue();
            if (StringUtils.hasText(token)) {
                return token.trim();
            }
        } catch (Exception ignored) {
            // 无登录会话：按内部调用处理，不携带 token
        }
        return null;
    }

    /**
     * 关闭异步登记线程池。
     */
    @PreDestroy
    public void shutdown() {
        registrationExecutor.shutdownNow();
    }
}
