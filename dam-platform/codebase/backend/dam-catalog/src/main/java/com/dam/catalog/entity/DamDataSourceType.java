package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "dam_data_source_type", schema = "dam_metadata")
public class DamDataSourceType {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 类型编码: MYSQL, POSTGRESQL, ORACLE 等 */
    private String typeKey;
    /** 显示名称: MySQL, PostgreSQL, Oracle 等 */
    private String typeName;
    /** JDBC 驱动类全限定名 */
    private String driverClass;
    /** JDBC URL 模板, 占位符 %s(host) %d(port) %s(databaseName) */
    private String urlTemplate;
    /** 默认端口 */
    private Integer defaultPort;
    /**
     * catalog 策略:
     * database - 使用 databaseName 作为 catalog
     * null - catalog 传 null (Oracle/SQLServer)
     * schema - 不使用 catalog
     */
    private String catalogMode;
    /**
     * 主键查询策略:
     * information_schema - 标准 INFORMATION_SCHEMA 查询
     * pg_catalog - PostgreSQL 系统表直查
     * jdbc_get_primary_keys - 逐个表调用 JDBC getPrimaryKeys
     */
    private String pkStrategy;
    /**
     * schema 系统过滤关键字(逗号分隔), 如: information_schema,pg_catalog
     */
    private String systemSchemas;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态: 0禁用 1启用 */
    private Integer status;
    /** 备注 */
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
