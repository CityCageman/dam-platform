package com.dam.service.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据 API 运行结果
 * <p>
 * 仅包含列信息、数据行、行数与耗时；上游连接配置（URL、用户名、密码）不会出现在
 * 响应中。
 *
 * @author DAM Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataApiExecutionResult {

    /** 结果集列信息 */
    private List<DataApiColumn> columns;

    /** 数据行，key 为列标签（重复标签会追加序号后缀） */
    private List<Map<String, Object>> rows;

    /** 返回行数（最大 1000） */
    private int rowCount;

    /** 执行耗时（毫秒，含建连与查询） */
    private long elapsedMs;
}
