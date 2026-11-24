package com.dam.platform.scheduler.client;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
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

/**
 * TaskCenter 回调客户端。
 * <p>
 * 顺序 SQL DAG 执行器在开始执行前调用 {@code POST /api/task-center/callbacks/start}
 * 登记运行实例（TaskCenter 侧为 PENDING），执行结束后调用
 * {@code POST /api/task-center/callbacks/{instanceId}/finish} 回写 SUCCESS/FAILED。
 * 登记与回写统一归口在 {@code ScheduleRunExecutor}，手动触发与 Cron 派发不再各自登记，
 * 避免同一个 run 在 TaskCenter 产生重复实例。
 * <p>
 * 调度可用性优先：连接/读取超时设置得较短，任何调用失败只记录 warn，不向上抛出；
 * TaskCenter 不可用不会阻塞、回滚或改变本地 schedule_run 的运行状态。
 * {@link #start} 在登记失败时返回 {@code null}，调用方据此跳过随后的 finish。
 * <p>
 * 鉴权沿用调用方 Sa-Token：优先读取当前请求的 {@code token} 请求头，
 * 定时任务线程没有请求上下文时退化为无 token 的内部调用；异步执行线程可通过
 * {@link #captureToken()} 在提交前捕获 token 并显式透传。
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

    /** 回调结束接口路径前缀 */
    private static final String FINISH_PATH_PREFIX = "/api/task-center/callbacks/";

    /** 回调结束接口路径后缀 */
    private static final String FINISH_PATH_SUFFIX = "/finish";

    /** 结束回调允许的终态 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 结束回调允许的终态 */
    private static final String STATUS_FAILED = "FAILED";

    /** TaskCenter title 字段最大长度 */
    private static final int MAX_TITLE_LENGTH = 200;

    /** TaskCenter errorMessage 字段最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

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
    }

    /**
     * 登记一次运行实例，token 取自当前线程上下文。
     *
     * @param jobCode 调度任务编码，作为 sourceTaskCode
     * @param jobName 调度任务名称，用于拼接 title
     * @param runId   本地 schedule_run 主键，作为 sourceRunId
     * @return TaskCenter 实例ID；登记失败或未返回实例ID时返回 {@code null}
     */
    public Long start(String jobCode, String jobName, Long runId) {
        return start(jobCode, jobName, runId, captureToken());
    }

    /**
     * 登记一次运行实例，并显式传入调用线程捕获的 token。
     * <p>
     * 所有异常（参数缺失、TaskCenter 不可用、返回非 200、响应缺少 instanceId）都只记录
     * warn 并返回 {@code null}，不向上抛出，不影响本地 DAG 执行。
     *
     * @param jobCode 调度任务编码，作为 sourceTaskCode
     * @param jobName 调度任务名称，用于拼接 title
     * @param runId   本地 schedule_run 主键，作为 sourceRunId
     * @param token   透传的 Sa-Token，可为空
     * @return TaskCenter 实例ID；失败时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public Long start(String jobCode, String jobName, Long runId, String token) {
        if (!StringUtils.hasText(jobCode) || runId == null) {
            log.warn("TaskCenter 开始回调参数不完整，跳过登记: jobCode={}, runId={}", jobCode, runId);
            return null;
        }

        try {
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
                log.warn("TaskCenter 登记运行开始返回失败，本地继续执行: jobCode={}, runId={}, "
                                + "code={}, message={}",
                        jobCode, runId, code, response == null ? null : response.get("message"));
                return null;
            }

            Long instanceId = extractInstanceId(response);
            if (instanceId == null) {
                log.warn("TaskCenter 开始回调响应缺少 instanceId，本地继续执行: jobCode={}, runId={}, "
                        + "response={}", jobCode, runId, response);
                return null;
            }
            log.info("TaskCenter 运行登记成功: jobCode={}, runId={}, instanceId={}",
                    jobCode, runId, instanceId);
            return instanceId;
        } catch (Exception e) {
            log.warn("TaskCenter 登记运行开始异常，本地继续执行: jobCode={}, runId={}, error={}",
                    jobCode, runId, e.toString());
            return null;
        }
    }

    /**
     * 回填运行结束，token 取自当前线程上下文。
     *
     * @param instanceId   TaskCenter 实例ID，为 {@code null} 时直接跳过
     * @param status       终态，仅支持 SUCCESS/FAILED
     * @param errorMessage 失败原因，成功时传 {@code null}
     */
    public void finish(Long instanceId, String status, String errorMessage) {
        finish(instanceId, status, errorMessage, captureToken());
    }

    /**
     * 回填运行结束，并显式传入调用线程捕获的 token。
     * <p>
     * 仅在 start 成功拿到 instanceId 后调用；任何异常只记录 warn，不向上抛出，
     * 不影响本地 schedule_run 的终态与调度线程。
     *
     * @param instanceId   TaskCenter 实例ID，为 {@code null} 时直接跳过
     * @param status       终态，仅支持 SUCCESS/FAILED
     * @param errorMessage 失败原因，成功时传 {@code null}
     * @param token        透传的 Sa-Token，可为空
     */
    @SuppressWarnings("unchecked")
    public void finish(Long instanceId, String status, String errorMessage, String token) {
        if (instanceId == null) {
            return;
        }
        if (!STATUS_SUCCESS.equals(status) && !STATUS_FAILED.equals(status)) {
            log.warn("TaskCenter 结束回调状态非法，仅支持 SUCCESS/FAILED: instanceId={}, status={}",
                    instanceId, status);
            return;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            if (STATUS_FAILED.equals(status)) {
                String safeError = truncateError(errorMessage);
                if (safeError != null) {
                    body.put("errorMessage", safeError);
                }
            }

            Map<String, Object> response = restClient.post()
                    .uri(FINISH_PATH_PREFIX + instanceId + FINISH_PATH_SUFFIX)
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
                log.warn("TaskCenter 回填运行结束返回失败，不影响本地运行状态: instanceId={}, "
                                + "status={}, code={}, message={}",
                        instanceId, status, code,
                        response == null ? null : response.get("message"));
                return;
            }
            log.info("TaskCenter 运行结束回写成功: instanceId={}, status={}", instanceId, status);
        } catch (Exception e) {
            log.warn("TaskCenter 回填运行结束异常，不影响本地运行状态: instanceId={}, status={}, error={}",
                    instanceId, status, e.toString());
        }
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
     * 裁剪失败原因到 TaskCenter errorMessage 上限。
     *
     * @param errorMessage 原始失败原因
     * @return 不超过 2000 字符的失败原因；空白时返回 null
     */
    private static String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        String value = errorMessage.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_ERROR_MESSAGE_LENGTH
                ? value.substring(0, MAX_ERROR_MESSAGE_LENGTH) : value;
    }

    /**
     * 捕获当前线程的 Sa-Token，供异步执行线程显式透传。
     * <p>
     * 优先读取当前请求的 {@code token} 请求头，无请求上下文或无登录态时返回 null。
     *
     * @return Sa-Token 值，可为 null
     */
    public static String captureToken() {
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
}
