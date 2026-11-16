package com.dam.integration.client;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 统一任务中心回调客户端（尽力而为）。
 * <p>
 * 集成执行器在运行开始/结束时调用 dam-task-center 的回调接口，把运行登记为统一任务实例：
 * <ul>
 *   <li>{@code POST /api/task-center/callbacks/start} 登记开始，返回实例 ID；</li>
 *   <li>{@code POST /api/task-center/callbacks/{instanceId}/finish} 回填终态与读写行数。</li>
 * </ul>
 * 任务中心是旁路观测能力，不是集成执行的前置条件：本客户端的所有方法都自行捕获异常并
 * 只记录 warn 日志，start 失败返回 {@code null}，finish 失败直接忽略，绝不向上抛出，
 * 避免任务中心不可用影响集成同步结果。
 * <p>
 * 服务地址由 {@code dam.task-center-url}（默认 {@code DAM_TASK_CENTER_URL} 或
 * {@code http://127.0.0.1:8099}）配置；连接/读取超时分别限制为
 * {@value #CONNECT_TIMEOUT_MILLIS}ms / {@value #READ_TIMEOUT_MILLIS}ms，防止回调拖慢执行线程。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class TaskCenterClient {

    /** 来源服务标识，与任务中心约定一致 */
    private static final String SOURCE_SERVICE = "dam-integration";

    /** 统一任务类型：数据集成 */
    private static final String TASK_TYPE_INTEGRATION = "INTEGRATION";

    /** 任务中心默认地址 */
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8099";

    /** 建立连接超时（毫秒）：任务中心不可达时快速失败，不阻塞同步线程 */
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;

    /** 读取响应超时（毫秒），避免任务中心卡住导致集成运行长时间不开始 */
    private static final int READ_TIMEOUT_MILLIS = 5000;

    /** 任务中心 title 最大长度 */
    private static final int MAX_TITLE_LENGTH = 200;

    /** 任务中心 errorMessage 最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /** 日志中响应/异常文本的最大长度 */
    private static final int MAX_WARN_TEXT_LENGTH = 500;

    /** 任务中心基础地址，仅用于日志定位 */
    private final String baseUrl;

    /** 任务中心 HTTP 客户端 */
    private final RestClient restClient;

    /**
     * 构造任务中心客户端。
     *
     * @param baseUrl 任务中心基础地址
     */
    public TaskCenterClient(
            @Value("${dam.task-center-url:${DAM_TASK_CENTER_URL:http://127.0.0.1:8099}}")
            String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 登记集成运行开始。
     * <p>
     * 以 {@code sourceService=dam-integration}、{@code sourceTaskCode=jobCode}、
     * {@code sourceRunId=runId} 作为幂等键；任务中心返回的实例 ID 由调用方保存为局部变量，
     * 不透传到数据库。
     *
     * @param jobCode 集成任务编码
     * @param jobName 集成任务名称，用于 title
     * @param runId   集成运行记录 ID
     * @param runNo   同一任务内的运行序号，可为空（为空时标题回退使用 runId）
     * @return 任务中心实例 ID；任务中心不可用、返回异常或未返回实例 ID 时返回 {@code null}
     */
    public Long start(String jobCode, String jobName, Long runId, Integer runNo) {
        if (!hasText(jobCode) || runId == null) {
            log.warn("任务中心开始回调参数不完整，跳过登记: jobCode={}, runId={}", jobCode, runId);
            return null;
        }
        try {
            JSONObject body = new JSONObject();
            body.set("sourceService", SOURCE_SERVICE);
            body.set("sourceTaskCode", jobCode);
            body.set("sourceRunId", String.valueOf(runId));
            body.set("taskType", TASK_TYPE_INTEGRATION);
            body.set("title", buildTitle(jobName, runId, runNo));
            body.set("autoCreateDefinition", true);

            String raw = post("/api/task-center/callbacks/start", body.toString());
            if (!hasText(raw)) {
                log.warn("任务中心开始回调返回空响应，集成运行继续执行: runId={}, jobCode={}",
                        runId, jobCode);
                return null;
            }
            JSONObject root = JSONUtil.parseObj(raw);
            Integer code = root.getInt("code");
            if (code != null && code != 200) {
                log.warn("任务中心登记集成运行开始失败，集成运行继续执行: runId={}, code={}, message={}",
                        runId, code, abbreviate(root.getStr("message")));
                return null;
            }
            JSONObject data = root.getJSONObject("data");
            Long instanceId = data == null ? null : data.getLong("instanceId");
            if (instanceId == null) {
                log.warn("任务中心开始回调未返回 instanceId，集成运行继续执行: runId={}, response={}",
                        runId, abbreviate(raw));
                return null;
            }
            log.info("任务中心已登记集成运行开始: runId={}, instanceId={}", runId, instanceId);
            return instanceId;
        } catch (Exception e) {
            log.warn("任务中心不可用，集成运行继续执行: runId={}, jobCode={}, error={}",
                    runId, jobCode, abbreviate(e.toString()));
            return null;
        }
    }

    /**
     * 回填集成运行结束。
     * <p>
     * 仅在 start 成功拿到实例 ID 时调用；所有异常只记录 warn，不影响集成运行记录与任务状态。
     *
     * @param instanceId   任务中心实例 ID，为 {@code null} 时直接跳过
     * @param status       SUCCESS / FAILED
     * @param rowsRead     读取行数
     * @param rowsWritten  已提交写入行数
     * @param errorMessage 失败原因，成功时传 {@code null}
     */
    public void finish(Long instanceId, String status, long rowsRead, long rowsWritten,
                       String errorMessage) {
        if (instanceId == null) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.set("status", status);
            body.set("rowsRead", rowsRead);
            body.set("rowsWritten", rowsWritten);
            if (!"SUCCESS".equals(status) && hasText(errorMessage)) {
                body.set("errorMessage",
                        truncate(errorMessage.trim(), MAX_ERROR_MESSAGE_LENGTH));
            }

            String raw = post("/api/task-center/callbacks/" + instanceId + "/finish",
                    body.toString());
            if (!hasText(raw)) {
                // 结束回调已发出；响应为空只影响观测，不影响集成结果
                return;
            }
            JSONObject root = JSONUtil.parseObj(raw);
            Integer code = root.getInt("code");
            if (code != null && code != 200) {
                log.warn("任务中心回填集成运行结束失败，不影响集成结果: instanceId={}, status={}, "
                                + "code={}, message={}",
                        instanceId, status, code, abbreviate(root.getStr("message")));
                return;
            }
            log.info("任务中心已回填集成运行结束: instanceId={}, status={}, rowsRead={}, rowsWritten={}",
                    instanceId, status, rowsRead, rowsWritten);
        } catch (Exception e) {
            log.warn("任务中心回填集成运行结束异常，不影响集成结果: instanceId={}, status={}, error={}",
                    instanceId, status, abbreviate(e.toString()));
        }
    }

    /**
     * 发送 JSON POST 请求。
     *
     * @param path     相对路径
     * @param jsonBody JSON 请求体
     * @return 响应文本
     */
    private String post(String path, String jsonBody) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        String token = token();
        if (hasText(token)) {
            request.header("token", token);
        }
        return request.body(jsonBody).retrieve().body(String.class);
    }

    /**
     * 透传当前请求 token。
     * <p>
     * 优先读取 Sa-Token 请求头；异步执行线程没有请求上下文时再尝试
     * {@link StpUtil#getTokenValue()}，均不可用时按无 token 的内部调用处理。
     *
     * @return token 或 null
     */
    private static String token() {
        try {
            String token = SaHolder.getRequest().getHeader("token");
            if (hasText(token)) {
                return token.trim();
            }
        } catch (Exception ignored) {
            // 异步线程没有 Sa-Token 请求上下文，继续尝试无 token 调用
        }
        try {
            String token = StpUtil.getTokenValue();
            if (hasText(token)) {
                return token.trim();
            }
        } catch (Exception ignored) {
            // 无登录会话：按内部调用处理
        }
        return null;
    }

    /**
     * 构造运行标题：{@code jobName #runNo}，runNo 缺失时回退为 runId。
     *
     * @param jobName 任务名称
     * @param runId   运行记录 ID
     * @param runNo   运行序号
     * @return 截断到任务中心 title 上限的标题
     */
    private static String buildTitle(String jobName, Long runId, Integer runNo) {
        String name = hasText(jobName) ? jobName.trim() : "integration-job";
        Object sequence = runNo == null ? runId : runNo;
        return truncate(name + " #" + sequence, MAX_TITLE_LENGTH);
    }

    /**
     * 规范化基础地址：空值回退默认地址，去掉尾部斜杠。
     *
     * @param value 配置值
     * @return 规范化后的地址
     */
    private static String normalizeBaseUrl(String value) {
        String url = hasText(value) ? value.trim() : DEFAULT_BASE_URL;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? DEFAULT_BASE_URL : url;
    }

    /**
     * 截断日志文本，避免异常信息过长。
     *
     * @param value 原始文本
     * @return 截断后的文本
     */
    private static String abbreviate(String value) {
        return truncate(value, MAX_WARN_TEXT_LENGTH);
    }

    /**
     * 截断文本。
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本；入参为 null 时返回 null
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 判断文本是否包含非空白字符。
     *
     * @param value 文本
     * @return 是否非空白
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
