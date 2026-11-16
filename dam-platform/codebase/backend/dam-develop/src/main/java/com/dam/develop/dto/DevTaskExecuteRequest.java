package com.dam.develop.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

/**
 * 执行 SQL 任务请求
 * <p>
 * 只允许为 SQL 任务传入命名参数：SQL 中的 {@code :name} 按出现顺序绑定为 JDBC
 * 占位符，参数值通过本请求的 {@code params} 提供。参数值仅支持字符串、数字、
 * 布尔与 null；数组/对象等复合值不会绑定，避免驱动类型推断不确定。
 *
 * @author DAM Platform
 */
@Data
public class DevTaskExecuteRequest {

    /**
     * 命名参数（可选）
     * <p>
     * key 与 SQL 中的 {@code :name} 完全一致（区分大小写）；未在 SQL 中出现的
     * 多余参数会被忽略，SQL 中出现的参数缺失时返回 400。
     */
    @JsonAlias("parameters")
    private Map<String, Object> params;
}
