package com.dam.develop.dto;

import java.util.List;
import java.util.Map;

/**
 * SQL 任务参数与结果列信息
 * <p>
 * 用于编辑器提示：返回 SQL 中出现的命名参数（按首次出现顺序去重）以及执行该
 * 查询时结果集的列信息。列信息通过 {@code SELECT * FROM (<sql>) ... LIMIT 0}
 * 探测，不读取业务数据。
 *
 * @param params  SQL 中的命名参数名，按首次出现顺序去重
 * @param columns 结果列信息，每项包含 name（列标签）与 type（数据库类型名）
 * @author DAM Platform
 */
public record DevTaskSchemaResult(
        List<String> params,
        List<Map<String, Object>> columns) {
}
