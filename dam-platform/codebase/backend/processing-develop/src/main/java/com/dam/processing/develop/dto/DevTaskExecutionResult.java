package com.dam.processing.develop.dto;

import java.util.List;
import java.util.Map;

/**
 * SQL 任务执行结果
 * <p>
 * 一次只读查询的返回数据：列信息、行数据、行数与耗时。本服务不写执行历史表，
 * 结果只在本次响应中返回，避免产生无法核验的执行记录。
 *
 * @param columns   列信息，每项包含 name（列标签）与 type（数据库类型名）
 * @param rows      行数据，键与 {@code columns[].name} 对应；重复列名会追加 _2/_3 后缀
 * @param rowCount  实际返回行数（受配置 maxRows 限制）
 * @param elapsedMs 从建立连接到读取完成的耗时（毫秒）
 * @author DAM Platform
 */
public record DevTaskExecutionResult(
        List<Map<String, Object>> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        long elapsedMs) {
}
