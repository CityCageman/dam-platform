package com.dam.service.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据 API 查询结构（Schema）信息
 * <p>
 * 返回查询中出现的命名参数（按出现顺序）与结果列信息，供调用方在真正执行前
 * 了解参数与返回结构。列信息通过 {@code LIMIT 0} 探测，不返回真实数据。
 *
 * @author DAM Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataApiSchemaResult {

    /** 查询中的命名参数（去重后按首次出现顺序排列） */
    private List<String> params;

    /** 结果列信息 */
    private List<DataApiColumn> columns;
}
