package com.dam.scheduler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 调度执行引擎与 DolphinScheduler 接入配置。
 * <p>
 * 对应 {@code dam.scheduler.engine} 与 {@code dam.scheduler.dolphin.*}。默认
 * {@code engine=LOCAL}、{@code dolphin.enabled=false}，即保持原有本地顺序 SQL DAG
 * 执行器语义；只有显式切换到 DOLPHIN（全局默认值或单个任务列）并完成 base-url/
 * token/project-code 配置后才会访问 DolphinScheduler。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>base-url 只允许 http/https，必须由部署配置提供，接口不接受用户传入 URL；</li>
 *   <li>base-url 禁止包含用户信息、查询参数与锚点，避免凭据混入日志；</li>
 *   <li>token 不参与 toString/日志输出，配置缺失只汇总字段名，不输出字段值；</li>
 *   <li>配置非法不会导致服务启动失败，只在引擎状态/触发运行时返回明确的未配置原因。</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Component
@ConfigurationProperties(prefix = "dam.scheduler")
@Getter
@Setter
public class DolphinSchedulerProperties {

    /** 执行引擎：本地顺序 SQL DAG */
    public static final String ENGINE_LOCAL = "LOCAL";

    /** 执行引擎：DolphinScheduler */
    public static final String ENGINE_DOLPHIN = "DOLPHIN";

    /** base-url 最大长度，避免异常配置拖垮解析 */
    private static final int MAX_BASE_URL_LENGTH = 500;

    /** 允许的 base-url 协议 */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** 全局默认执行引擎，仅取 LOCAL/DOLPHIN，非法值按 LOCAL 处理 */
    private String engine = ENGINE_LOCAL;

    /** DolphinScheduler 接入配置 */
    private Dolphin dolphin = new Dolphin();

    /**
     * 归一化后的全局执行引擎。
     * <p>
     * 配置非法时降级为 LOCAL，避免启动失败或误触发外部调度。
     *
     * @return LOCAL 或 DOLPHIN
     */
    public String resolvedEngine() {
        if (!StringUtils.hasText(engine)) {
            return ENGINE_LOCAL;
        }
        String value = engine.trim().toUpperCase(Locale.ROOT);
        return ENGINE_DOLPHIN.equals(value) ? ENGINE_DOLPHIN : ENGINE_LOCAL;
    }

    /**
     * 全局默认引擎是否为 DOLPHIN。
     *
     * @return true 表示新建任务默认使用 DOLPHIN
     */
    public boolean isDolphinEngine() {
        return ENGINE_DOLPHIN.equals(resolvedEngine());
    }

    /**
     * DolphinScheduler 总开关是否打开。
     *
     * @return true 表示允许访问 DS
     */
    public boolean isDolphinEnabled() {
        return dolphin != null && dolphin.isEnabled();
    }

    /**
     * DolphinScheduler 必填连接配置是否齐全且合法。
     * <p>
     * 与 {@code enabled} 相互独立：允许先测试配置，再打开开关。
     *
     * @return true 表示 base-url/token/project-code 均已配置且合法
     */
    public boolean isDolphinConfigured() {
        return configurationIssues().isEmpty();
    }

    /**
     * 解析 DS 项目编码。
     *
     * @return 项目编码；未配置或非数字时返回 {@code null}
     */
    public Long dolphinProjectCode() {
        if (dolphin == null || !StringUtils.hasText(dolphin.getProjectCode())) {
            return null;
        }
        try {
            long code = Long.parseLong(dolphin.getProjectCode().trim());
            return code > 0 ? code : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 汇总连接配置问题。
     * <p>
     * 只返回字段名与原因，不返回 base-url/token 的实际值，可直接对外展示。
     *
     * @return 问题列表；为空表示配置完整
     */
    public List<String> configurationIssues() {
        Dolphin ds = dolphin == null ? new Dolphin() : dolphin;
        List<String> issues = new ArrayList<>();

        String baseUrl = trimToNull(ds.getBaseUrl());
        if (baseUrl == null) {
            issues.add("dolphin.base-url 未配置");
        } else {
            validateBaseUrl(baseUrl, issues);
        }
        if (!StringUtils.hasText(ds.getToken())) {
            issues.add("dolphin.token 未配置");
        }
        String projectCode = trimToNull(ds.getProjectCode());
        if (projectCode == null) {
            issues.add("dolphin.project-code 未配置");
        } else {
            try {
                if (Long.parseLong(projectCode) <= 0) {
                    issues.add("dolphin.project-code 必须为正整数");
                }
            } catch (NumberFormatException e) {
                issues.add("dolphin.project-code 必须为数字");
            }
        }
        return List.copyOf(issues);
    }

    /**
     * 校验 base-url 的协议、主机与凭据形态，防止把任意 URL 当作 DS 地址。
     *
     * @param raw    原始 base-url
     * @param issues 问题收集列表
     */
    private static void validateBaseUrl(String raw, List<String> issues) {
        if (raw.length() > MAX_BASE_URL_LENGTH) {
            issues.add("dolphin.base-url 长度超过 " + MAX_BASE_URL_LENGTH);
            return;
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            issues.add("dolphin.base-url 不允许包含空白字符");
            return;
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SCHEMES.contains(scheme)) {
                issues.add("dolphin.base-url 仅支持 http/https");
                return;
            }
            if (!StringUtils.hasText(uri.getHost())) {
                issues.add("dolphin.base-url 缺少主机名");
            }
            if (uri.getUserInfo() != null) {
                issues.add("dolphin.base-url 不允许包含用户信息");
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                issues.add("dolphin.base-url 不允许包含查询参数或锚点");
            }
        } catch (URISyntaxException e) {
            issues.add("dolphin.base-url 不是合法 URL");
        }
    }

    /**
     * 裁剪并归一化空白文本。
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
     * DolphinScheduler 连接配置。
     * <p>
     * token 为敏感信息：toString 固定脱敏，禁止通过日志/错误信息回显。
     */
    @Getter
    @Setter
    public static class Dolphin {

        /** 总开关，false 时不访问 DS，DOLPHIN 引擎任务直接失败并给出明确原因 */
        private boolean enabled;

        /** DS API 基础地址，必须包含 /dolphinscheduler 上下文，仅支持 http/https */
        private String baseUrl;

        /** DS 访问 token，通过请求头 token 传递 */
        private String token;

        /** DS 项目编码 */
        private String projectCode;

        /** 默认租户编码，任务未单独指定时使用，可为空 */
        private String tenantCode;

        /** 建连超时（毫秒） */
        private int connectTimeoutMs = 3000;

        /** 读取超时（毫秒） */
        private int readTimeoutMs = 10000;

        /** 运行实例状态轮询间隔（毫秒） */
        private long pollIntervalMs = 15000;

        /** 单轮最多轮询的运行记录数，避免扫描无界 */
        private int pollBatchSize = 50;

        /**
         * 脱敏字符串表示，token 与 base-url 只输出是否配置。
         *
         * @return 不含凭据的配置摘要
         */
        @Override
        public String toString() {
            return "Dolphin[enabled=" + enabled
                    + ", baseUrl=" + (StringUtils.hasText(baseUrl) ? "***" : "null")
                    + ", token=" + (StringUtils.hasText(token) ? "***" : "null")
                    + ", projectCode=" + projectCode
                    + ", tenantCode=" + tenantCode
                    + ", connectTimeoutMs=" + connectTimeoutMs
                    + ", readTimeoutMs=" + readTimeoutMs
                    + ", pollIntervalMs=" + pollIntervalMs
                    + ", pollBatchSize=" + pollBatchSize + "]";
        }
    }
}
