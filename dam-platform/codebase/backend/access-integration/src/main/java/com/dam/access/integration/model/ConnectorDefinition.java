package com.dam.access.integration.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据集成连接器类型定义
 * <p>
 * 阶段 1 为静态目录数据，描述平台支持的连接器类型及其启用状态；
 * 后续可切换为 dam_integration 库表中的连接器注册信息。
 *
 * @param code        连接器编码，如 JDBC、CDC、FILE、API、KAFKA
 * @param name        连接器名称
 * @param description 连接器说明
 * @param status      连接器状态，阶段 1 统一为 AVAILABLE
 * @author DAM Platform
 */
public record ConnectorDefinition(
        String code,
        String name,
        String description,
        String status) {

    /** 连接器可用状态 */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 连接器规划中状态（预留给后续按连接器能力动态标记） */
    public static final String STATUS_PLANNED = "PLANNED";

    /**
     * 转换为前端友好的 Map 结构（保持字段顺序）
     *
     * @return 连接器字段 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", description);
        map.put("status", status);
        return map;
    }
}
