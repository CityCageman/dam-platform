package com.dam.platform.portal.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门户模块定义
 * <p>
 * 对应统一门户中能力域下的一个功能模块。阶段 1 全部为静态配置，
 * status 暂统一为 AVAILABLE；规划中的模块可由前端自行标记。
 *
 * @param code        模块编码（如 DAM、MDM）
 * @param name        模块名称
 * @param description 模块简介
 * @param icon        前端图标名称
 * @param route       前端路由，形如 /domains/{domainCode}/{moduleCode 小写}
 * @param sortOrder   模块在能力域内的展示顺序
 * @param status      模块状态，阶段 1 统一为 AVAILABLE
 * @author DAM Platform
 */
public record PortalModule(
        String code,
        String name,
        String description,
        String icon,
        String route,
        int sortOrder,
        String status) {

    /** 模块可用状态 */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 模块规划中状态 */
    public static final String STATUS_PLANNED = "PLANNED";

    /**
     * 转换为前端友好的 Map 结构（保持字段顺序）
     *
     * @return 模块字段 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", description);
        map.put("icon", icon);
        map.put("route", route);
        map.put("sortOrder", sortOrder);
        map.put("status", status);
        return map;
    }
}
