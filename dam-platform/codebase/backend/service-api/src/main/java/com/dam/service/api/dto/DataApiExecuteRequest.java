package com.dam.service.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数据 API 运行请求
 * <p>
 * {@code params} 为命名参数键值对，运行时会按查询中 {@code :name} 的出现顺序
 * 绑定到 PreparedStatement，不会拼接进 SQL 文本。
 *
 * @author DAM Platform
 */
@Data
public class DataApiExecuteRequest {

    /** 命名参数（键为查询中的参数名，不含冒号） */
    private Map<String, Object> params;
}
