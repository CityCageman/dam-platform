package com.dam.platform.scheduler.client;

import com.dam.platform.scheduler.config.DolphinSchedulerProperties;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessDefinition;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessDefinitionListResult;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessInstance;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessInstanceResult;
import com.dam.platform.scheduler.dto.dolphin.DolphinProbeResult;
import com.dam.platform.scheduler.dto.dolphin.DolphinStartResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DolphinScheduler 3.x API 客户端（PoC）。
 * <p>
 * 只使用部署配置中的 base-url/token/project-code，接口不接收用户传入的 URL，避免 SSRF。
 * 所有方法返回结构化 DTO、不向调用方抛出异常；错误信息统一脱敏（token、base-url、
 * HTTP userinfo 不会出现在日志或 error_message 中）。token 通过请求头 {@code token}
 * 传递（DS 3.x 惯用方式），建连/读取超时来自配置。
 * <p>
 * 依赖的 DS 3.x 接口：
 * <ul>
 *   <li>{@code POST /projects/{projectCode}/executors/start-process-instance}</li>
 *   <li>{@code GET /projects/{projectCode}/process-instances/{id}}</li>
 *   <li>{@code GET /projects/{projectCode}/process-definitions?pageNo=1&pageSize=100}</li>
 * </ul>
 * DS 统一响应为 {@code {"code":0,"msg":"success","data":...}}，本客户端把非 0 或
 * 缺少 data 的响应视为失败。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class DolphinSchedulerClient {

    /** 启动流程实例（DS 3.x） */
    private static final String START_PATH =
            "/projects/{projectCode}/executors/start-process-instance";

    /** 查询流程实例（DS 3.x） */
    private static final String INSTANCE_PATH =
            "/projects/{projectCode}/process-instances/{processInstanceId}";

    /** 查询流程定义列表（DS 3.x） */
    private static final String DEFINITION_PATH =
            "/projects/{projectCode}/process-definitions?pageNo=1&pageSize=100";

    /** DS 执行类型：启动流程 */
    private static final String EXECUTION_TYPE_START = "START_PROCESS";

    /** 单次错误信息最大长度 */
    private static final int MAX_MESSAGE_LENGTH = 500;

    /** 错误响应体最大长度 */
    private static final int MAX_ERROR_BODY_LENGTH = 200;

    /** 建连超时下限/上限（毫秒） */
    private static final int MIN_CONNECT_TIMEOUT_MS = 500;
    private static final int MAX_CONNECT_TIMEOUT_MS = 60_000;

    /** 读取超时下限/上限（毫秒） */
    private static final int MIN_READ_TIMEOUT_MS = 1_000;
    private static final int MAX_READ_TIMEOUT_MS = 120_000;

    /** 调度执行引擎与 DS 配置 */
    private final DolphinSchedulerProperties properties;

    /** DS HTTP 客户端；配置不完整时为 null，所有调用返回未配置原因 */
    private final RestClient restClient;

    /**
     * 构造客户端。
     * <p>
     * 配置不完整时只禁用 DS 调用，不影响服务启动；后续引擎状态/测试接口会返回
     * 具体缺失字段。base-url 已在配置类中校验 http/https。
     *
     * @param properties 调度执行引擎与 DS 配置
     */
    public DolphinSchedulerClient(DolphinSchedulerProperties properties) {
        this.properties = properties;
        this.restClient = properties.isDolphinConfigured() ? buildRestClient(properties) : null;
    }

    /**
     * 当前是否具备访问 DS 的完整配置。
     *
     * @return true 表示 base-url/token/project-code 均已配置
     */
    public boolean isConfigured() {
        return restClient != null;
    }

    /**
     * 触发 DS 流程实例（非 dry-run 的便捷重载）。
     *
     * @param processDefinitionCode    流程定义编码
     * @param processDefinitionVersion 流程定义版本，可为空
     * @param tenantCode               租户编码，为空时回退到配置值
     * @return 结构化启动结果
     */
    public DolphinStartResult startProcess(Long processDefinitionCode,
                                           Integer processDefinitionVersion,
                                           String tenantCode) {
        return startProcess(processDefinitionCode, processDefinitionVersion, tenantCode, false);
    }

    /**
     * 触发 DS 流程实例。
     * <p>
     * 请求体按 DS 3.x {@code ExecuteWorkflowRequest} 构造：processDefinitionCode、
     * processDefinitionVersion（版本为空时不传，由 DS 侧决定）、tenantCode、
     * {@code executionType=START_PROCESS}、{@code startParams={}}。
     * <p>
     * {@code dryRun=true} 只做配置/参数校验并返回请求结构摘要，不调用 DS 启动接口，
     * 用于 PoC 阶段确认租户/版本等参数，避免产生真实流程实例。
     *
     * @param processDefinitionCode    流程定义编码，必填
     * @param processDefinitionVersion 流程定义版本，可为空
     * @param tenantCode               租户编码，为空时回退到配置值
     * @param dryRun                   是否干跑（不实际启动）
     * @return 结构化启动结果
     */
    public DolphinStartResult startProcess(Long processDefinitionCode,
                                           Integer processDefinitionVersion,
                                           String tenantCode,
                                           boolean dryRun) {
        if (processDefinitionCode == null || processDefinitionCode <= 0) {
            return new DolphinStartResult(false, null, dryRun,
                    "processDefinitionCode 必须为正整数");
        }
        if (processDefinitionVersion != null && processDefinitionVersion <= 0) {
            return new DolphinStartResult(false, null, dryRun,
                    "processDefinitionVersion 必须为正整数");
        }
        String configError = configurationError();
        if (configError != null) {
            return new DolphinStartResult(false, null, dryRun, configError);
        }
        if (dryRun) {
            // 只构造请求体做校验，日志只输出结构字段，不输出 token
            Map<String, Object> body = buildStartBody(processDefinitionCode,
                    processDefinitionVersion, tenantCode);
            log.info("DolphinScheduler 启动参数 dry-run 校验通过: processDefinitionCode={}, "
                            + "processDefinitionVersion={}, tenantCode={}, bodyFields={}",
                    processDefinitionCode, processDefinitionVersion, body.get("tenantCode"),
                    body.keySet());
            return new DolphinStartResult(true, null, true,
                    "dry-run：配置与启动参数校验通过，未调用 DolphinScheduler 启动接口");
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(START_PATH, projectCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyToken)
                    .body(buildStartBody(processDefinitionCode, processDefinitionVersion, tenantCode))
                    .retrieve()
                    .body(Map.class);
            return parseStartResponse(response);
        } catch (RestClientResponseException e) {
            return new DolphinStartResult(false, null, false, httpFailure(e));
        } catch (Exception e) {
            return new DolphinStartResult(false, null, false, callFailure(e));
        }
    }

    /**
     * 查询 DS 流程实例状态。
     *
     * @param processInstanceId DS 流程实例ID
     * @return 结构化查询结果；失败时 message 已脱敏
     */
    public DolphinProcessInstanceResult queryProcessInstance(Long processInstanceId) {
        if (processInstanceId == null || processInstanceId <= 0) {
            return new DolphinProcessInstanceResult(false, null,
                    "processInstanceId 必须为正整数");
        }
        String configError = configurationError();
        if (configError != null) {
            return new DolphinProcessInstanceResult(false, null, configError);
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(INSTANCE_PATH, projectCode(), processInstanceId)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyToken)
                    .retrieve()
                    .body(Map.class);
            return parseInstanceResponse(response, processInstanceId);
        } catch (RestClientResponseException e) {
            return new DolphinProcessInstanceResult(false, null, httpFailure(e));
        } catch (Exception e) {
            return new DolphinProcessInstanceResult(false, null, callFailure(e));
        }
    }

    /**
     * 查询 DS 流程定义列表（第一页，最多 100 条）。
     * <p>
     * 也作为连通性探测：能正常返回 {@code code=0} 说明 base-url、token、project-code
     * 三项配置可用。
     *
     * @return 结构化流程定义列表
     */
    public DolphinProcessDefinitionListResult listProcessDefinitions() {
        String configError = configurationError();
        if (configError != null) {
            return new DolphinProcessDefinitionListResult(false, 0, List.of(), configError);
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(DEFINITION_PATH, projectCode())
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyToken)
                    .retrieve()
                    .body(Map.class);
            return parseDefinitionResponse(response);
        } catch (RestClientResponseException e) {
            return new DolphinProcessDefinitionListResult(false, 0, List.of(), httpFailure(e));
        } catch (Exception e) {
            return new DolphinProcessDefinitionListResult(false, 0, List.of(), callFailure(e));
        }
    }

    /**
     * 连接性探测：先校验本地配置，再拉取流程定义列表。
     *
     * @return 探测结果（不触发任何流程实例）
     */
    public DolphinProbeResult probe() {
        String configError = configurationError();
        if (configError != null) {
            return new DolphinProbeResult(false, configError, null);
        }
        DolphinProcessDefinitionListResult result = listProcessDefinitions();
        if (!result.ok()) {
            return new DolphinProbeResult(false, result.message(), null);
        }
        return new DolphinProbeResult(true,
                "DolphinScheduler 连接正常，项目下流程定义 " + result.total() + " 个",
                result.definitions().size());
    }

    /**
     * 构造 DS 3.x 启动请求体。
     *
     * @param processDefinitionCode    流程定义编码
     * @param processDefinitionVersion 流程定义版本，可为空
     * @param tenantCode               租户编码，可为空
     * @return 请求体
     */
    private Map<String, Object> buildStartBody(Long processDefinitionCode,
                                               Integer processDefinitionVersion,
                                               String tenantCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processDefinitionCode", processDefinitionCode);
        if (processDefinitionVersion != null) {
            body.put("processDefinitionVersion", processDefinitionVersion);
        }
        String tenant = trimToNull(tenantCode);
        if (tenant == null) {
            tenant = trimToNull(properties.getDolphin().getTenantCode());
        }
        if (tenant != null) {
            body.put("tenantCode", tenant);
        }
        body.put("executionType", EXECUTION_TYPE_START);
        body.put("startParams", Collections.emptyMap());
        return body;
    }

    /**
     * 设置 DS 鉴权头；token 只出现在请求头，不写日志。
     *
     * @param headers 请求头
     */
    private void applyToken(org.springframework.http.HttpHeaders headers) {
        String token = trimToNull(properties.getDolphin().getToken());
        if (token != null) {
            headers.set("token", token);
        }
    }

    /**
     * 解析启动响应。
     * <p>
     * DS 3.x 的 {@code data} 可能是流程实例ID数字，也可能是
     * {@code {"processInstanceId":123}} 结构，两种都兼容。
     *
     * @param response DS 响应
     * @return 结构化启动结果
     */
    private DolphinStartResult parseStartResponse(Map<String, Object> response) {
        Integer code = codeOf(response);
        if (code == null || code != 0) {
            return new DolphinStartResult(false, null, false,
                    "DolphinScheduler 启动失败: " + describeFailure(code, response));
        }
        Long processInstanceId = extractProcessInstanceId(
                response == null ? null : response.get("data"));
        if (processInstanceId == null) {
            return new DolphinStartResult(false, null, false,
                    "DolphinScheduler 启动响应缺少 processInstanceId");
        }
        return new DolphinStartResult(true, processInstanceId, false,
                "DolphinScheduler 流程实例已提交");
    }

    /**
     * 解析流程实例响应。
     *
     * @param response          DS 响应
     * @param processInstanceId 请求使用的实例ID，响应未返回 id 时作为兜底
     * @return 结构化查询结果
     */
    private DolphinProcessInstanceResult parseInstanceResponse(Map<String, Object> response,
                                                               Long processInstanceId) {
        Integer code = codeOf(response);
        if (code == null || code != 0) {
            return new DolphinProcessInstanceResult(false, null,
                    "DolphinScheduler 查询流程实例失败: " + describeFailure(code, response));
        }
        Map<String, Object> data = asMap(response == null ? null : response.get("data"));
        if (data == null) {
            return new DolphinProcessInstanceResult(false, null,
                    "DolphinScheduler 查询流程实例响应缺少 data");
        }

        Long id = toLong(data.get("id"));
        if (id == null) {
            id = processInstanceId;
        }
        State state = parseState(data);
        DolphinProcessInstance instance = new DolphinProcessInstance(
                id,
                toText(data.get("name")),
                toLong(data.get("processDefinitionCode")),
                toInteger(data.get("processDefinitionVersion")),
                state.code(),
                state.name(),
                toText(data.get("startTime")),
                toText(data.get("endTime")));
        return new DolphinProcessInstanceResult(true, instance, "ok");
    }

    /**
     * 解析流程定义列表响应，兼容 totalList/records/list 与裸数组三种形态。
     *
     * @param response DS 响应
     * @return 结构化流程定义列表
     */
    private DolphinProcessDefinitionListResult parseDefinitionResponse(Map<String, Object> response) {
        Integer code = codeOf(response);
        if (code == null || code != 0) {
            return new DolphinProcessDefinitionListResult(false, 0, List.of(),
                    "DolphinScheduler 查询流程定义失败: " + describeFailure(code, response));
        }
        Object rawData = response == null ? null : response.get("data");
        List<?> rawList;
        Integer total = null;
        if (rawData instanceof List<?> list) {
            rawList = list;
        } else {
            Map<String, Object> data = asMap(rawData);
            if (data == null) {
                return new DolphinProcessDefinitionListResult(false, 0, List.of(),
                        "DolphinScheduler 查询流程定义响应缺少 data");
            }
            total = toInteger(data.get("total"));
            rawList = firstList(data, "totalList", "records", "list");
        }
        if (rawList == null) {
            rawList = List.of();
        }

        List<DolphinProcessDefinition> definitions = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            Map<String, Object> map = asMap(item);
            if (map == null) {
                continue;
            }
            definitions.add(new DolphinProcessDefinition(
                    toLong(map.get("code")),
                    toText(map.get("name")),
                    toInteger(map.get("version")),
                    toText(map.get("releaseState"))));
        }
        int resolvedTotal = total == null ? definitions.size() : total;
        return new DolphinProcessDefinitionListResult(true, resolvedTotal,
                List.copyOf(definitions), "ok");
    }

    /**
     * 解析状态码与状态名；兼容数字、数字字符串与枚举名。
     *
     * @param data 流程实例数据
     * @return 状态码/状态名组合
     */
    private static State parseState(Map<String, Object> data) {
        Object raw = firstNonNull(data, "state", "stateCode", "workflowExecutionStatus");
        Integer code = null;
        String name = null;
        if (raw instanceof Number number) {
            code = number.intValue();
            name = DolphinExecutionState.nameByCode(code);
        } else if (raw != null) {
            String text = String.valueOf(raw).trim();
            try {
                code = Integer.valueOf(text);
                name = DolphinExecutionState.nameByCode(code);
            } catch (NumberFormatException e) {
                name = text.toUpperCase(Locale.ROOT);
                code = DolphinExecutionState.codeByName(name);
            }
        }
        if (name == null) {
            name = toText(firstNonNull(data, "stateName", "stateDesc"));
            if (code == null && name != null) {
                // 某些 DS 版本只返回状态名，按名称兜底映射
                code = DolphinExecutionState.codeByName(name);
            }
        }
        return new State(code, name);
    }

    /**
     * 从 data 提取流程实例ID。
     *
     * @param data 响应 data
     * @return 流程实例ID，缺失返回 null
     */
    private static Long extractProcessInstanceId(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Number number) {
            return number.longValue();
        }
        if (data instanceof String text) {
            return toLong(text);
        }
        Map<String, Object> map = asMap(data);
        if (map == null) {
            return null;
        }
        return toLong(firstNonNull(map, "processInstanceId", "workflowInstanceId", "id"));
    }

    /**
     * 从统一响应读取 code。
     *
     * @param response DS 响应
     * @return code；缺失或非数字返回 null
     */
    private static Integer codeOf(Map<String, Object> response) {
        return response == null ? null : toInteger(response.get("code"));
    }

    /**
     * 描述 DS 失败响应，消息已脱敏。
     *
     * @param code     响应 code
     * @param response DS 响应
     * @return 失败描述
     */
    private String describeFailure(Integer code, Map<String, Object> response) {
        String message = response == null ? null
                : toText(firstNonNull(response, "msg", "message"));
        return "code=" + code + ", message=" + sanitize(message);
    }

    /**
     * 构造 HTTP 失败描述，响应体截断并脱敏。
     *
     * @param e HTTP 异常
     * @return 失败描述
     */
    private String httpFailure(RestClientResponseException e) {
        String body = sanitize(e.getResponseBodyAsString());
        if (body.length() > MAX_ERROR_BODY_LENGTH) {
            body = body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
        }
        return "DolphinScheduler 返回 HTTP " + e.getStatusCode().value()
                + (StringUtils.hasText(body) ? "：" + body : "");
    }

    /**
     * 构造调用异常描述，不带 URL/token。
     *
     * @param e 异常
     * @return 失败描述
     */
    private String callFailure(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = sanitize(root.getMessage());
        return "调用 DolphinScheduler 失败: " + root.getClass().getSimpleName()
                + (StringUtils.hasText(message) ? " - " + message : "");
    }

    /**
     * 未配置时的一致错误描述。
     *
     * @return 错误描述；配置完整返回 null
     */
    private String configurationError() {
        if (restClient != null) {
            return null;
        }
        List<String> issues = properties.configurationIssues();
        return "DolphinScheduler 未配置: " + String.join("; ", issues);
    }

    /**
     * 构建 RestClient：连接/读取超时来自配置，base-url 去掉尾部斜杠。
     *
     * @param properties 调度配置
     * @return RestClient
     */
    private static RestClient buildRestClient(DolphinSchedulerProperties properties) {
        DolphinSchedulerProperties.Dolphin dolphin = properties.getDolphin();
        int connectTimeoutMs = Math.clamp(dolphin.getConnectTimeoutMs(),
                MIN_CONNECT_TIMEOUT_MS, MAX_CONNECT_TIMEOUT_MS);
        int readTimeoutMs = Math.clamp(dolphin.getReadTimeoutMs(),
                MIN_READ_TIMEOUT_MS, MAX_READ_TIMEOUT_MS);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(stripTrailingSlash(dolphin.getBaseUrl().trim()))
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 读取当前 DS 项目编码。
     *
     * @return 项目编码
     */
    private Long projectCode() {
        Long code = properties.dolphinProjectCode();
        if (code == null) {
            throw new IllegalStateException("dolphin.project-code 未配置");
        }
        return code;
    }

    /**
     * 错误消息脱敏：替换 token、base-url、URL userinfo 与 token=xxx 形式。
     *
     * @param raw 原始消息
     * @return 脱敏后的消息
     */
    private String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String message = raw;
        DolphinSchedulerProperties.Dolphin dolphin = properties.getDolphin();
        if (dolphin != null) {
            String token = trimToNull(dolphin.getToken());
            if (token != null) {
                message = message.replace(token, "***");
            }
            String baseUrl = trimToNull(dolphin.getBaseUrl());
            if (baseUrl != null) {
                message = message.replace(baseUrl, "***");
                message = message.replace(stripTrailingSlash(baseUrl), "***");
            }
        }
        message = message.replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1***:***@");
        message = message.replaceAll("(?i)(token\\s*[=:]\\s*)[^\\s,;&\"']+", "$1***");
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "..." : message;
    }

    /**
     * 去掉字符串末尾斜杠。
     *
     * @param value 原始值
     * @return 去掉末尾斜杠的结果
     */
    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 类型转换：转 Map。
     *
     * @param value 原始值
     * @return Map 或 null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /**
     * 按优先顺序取第一个存在的 List 字段。
     *
     * @param map  数据
     * @param keys 候选字段
     * @return 列表；都不存在返回 null
     */
    private static List<?> firstList(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.get(key) instanceof List<?> list) {
                return list;
            }
        }
        return null;
    }

    /**
     * 按优先顺序取第一个非空字段。
     *
     * @param map  数据
     * @param keys 候选字段
     * @return 值或 null
     */
    private static Object firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 转 Long。
     *
     * @param value 原始值
     * @return Long 或 null
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转 Integer。
     *
     * @param value 原始值
     * @return Integer 或 null
     */
    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转字符串。
     *
     * @param value 原始值
     * @return 文本或 null
     */
    private static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 裁剪空白文本。
     *
     * @param raw 原始值
     * @return 裁剪后的文本或 null
     */
    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * DS 状态解析结果。
     *
     * @param code 状态码
     * @param name 状态名
     */
    private record State(Integer code, String name) {
    }
}
