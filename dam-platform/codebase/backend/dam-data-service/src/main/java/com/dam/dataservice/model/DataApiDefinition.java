package com.dam.dataservice.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据 API 定义
 * <p>
 * 描述一个可对外提供的数据 API：编码、名称、访问路径、HTTP 方法与发布状态。
 * 阶段 1 仅作为静态示例与接口契约，尚未映射数据库表。
 *
 * @param code   API 编码
 * @param name   API 名称
 * @param path   API 访问路径
 * @param method HTTP 方法（GET/POST 等）
 * @param status 发布状态（如 DRAFT、PUBLISHED、OFFLINE）
 * @author DAM Platform
 */
public record DataApiDefinition(
        String code,
        String name,
        String path,
        String method,
        String status) {

    /**
     * 转换为前端友好的 Map 结构（保持字段顺序）
     *
     * @return API 定义字段 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("path", path);
        map.put("method", method);
        map.put("status", status);
        return map;
    }
}
