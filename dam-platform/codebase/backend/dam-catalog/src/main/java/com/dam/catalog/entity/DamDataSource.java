package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_datasource", schema = "dam_metadata")
public class DamDataSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据源名称 */
    private String dsName;
    /** 数据源代码 */
    private String dsCode;
    /** 数据源类型 */
    private String dsType;
    /** 数据源适配器 */
    private String dsAdapter;
    /** 数据源IP */
    private String dsIp;
    /** 数据源端口 */
    private String dsPort;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClass;
    private String connectionProps;
    private String description;
    private Integer status;
    /**
     * 数据源所属数仓层级：SOURCE-源库，ODS-操作数据存储层，DWD-明细数据层，
     * DWS-汇总数据层，DIM-维度层，ADS-应用数据层，DWA-分析数据层
     */
    private String dataLayer;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 展示用派生字段（exist=false，不参与持久化） =====
    /** 数据库名/namespace（从 JDBC URL 解析，供前端表单构建 jdbcUrl） */
    @TableField(exist = false)
    private String databaseName;
    @TableField(exist = false)
    private String sourceSystem;
    /** 是否已设置密码（exist=false，仅供前端展示掩码，不泄露真实密码） */
    @TableField(exist = false)
    private Boolean hasPassword;
}
