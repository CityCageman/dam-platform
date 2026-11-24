package com.dam.service.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据 API 执行结果的列信息
 * <p>
 * 用于运行入口与 Schema 探测接口，只暴露列名、别名、数据库类型与 JDBC 类型码，
 * 不包含任何上游连接配置。
 *
 * @author DAM Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataApiColumn {

    /** 数据库原始列名 */
    private String name;

    /** 结果集列标签（SELECT 中定义的别名，无别名时与列名一致） */
    private String label;

    /** 数据库类型名（如 int4、varchar、timestamptz） */
    private String type;

    /** JDBC 类型码，对应 {@link java.sql.Types} 常量 */
    private int jdbcType;
}
