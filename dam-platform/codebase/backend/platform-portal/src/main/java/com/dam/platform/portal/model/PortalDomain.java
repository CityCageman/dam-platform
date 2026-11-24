package com.dam.platform.portal.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户能力域定义
 * <p>
 * 对应统一门户的 8 个能力域，每个能力域包含若干功能模块。
 *
 * @param code        能力域编码（如 asset、standard）
 * @param name        能力域名称
 * @param description 能力域简介
 * @param icon        前端图标名称
 * @param route       前端路由，形如 /domains/{domainCode}
 * @param sortOrder   能力域展示顺序
 * @param modules     能力域下的模块列表
 * @author DAM Platform
 */
public record PortalDomain(
        String code,
        String name,
        String description,
        String icon,
        String route,
        int sortOrder,
        List<PortalModule> modules) {

    /**
     * 紧凑构造器：复制模块列表，保证目录数据不可变
     */
    public PortalDomain {
        modules = List.copyOf(modules);
    }

    /**
     * 转换为前端友好的 Map 结构（保持字段顺序）
     *
     * @return 能力域字段 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", description);
        map.put("icon", icon);
        map.put("route", route);
        map.put("sortOrder", sortOrder);
        map.put("modules", modules.stream().map(PortalModule::toMap).toList());
        return map;
    }
}
