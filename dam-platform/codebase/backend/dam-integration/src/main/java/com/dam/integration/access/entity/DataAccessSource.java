package com.dam.integration.access.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 数据接入数据源实体
 * <p>
 * 对应 {@code dam_access.metadata_datasource}。数据源写入口与连接测试由 dam-integration
 * 统一负责；{@code dam_metadata.metadata_datasource} 仅为只读兼容视图。
 * <p>
 * 密码字段只接受请求入参，永不参与 JSON 序列化，避免任何接口意外回传密文或明文。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "metadata_datasource", schema = "dam_access")
public class DataAccessSource {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源名称 */
    private String dsName;

    /** 数据源代码（未删除数据内唯一） */
    private String dsCode;

    /** 数据源类型，如 POSTGRESQL/MYSQL/ORACLE/SQLSERVER/DM8/KINGBASE/IRIS */
    private String dsType;

    /** 数据源适配器，通常与类型一致 */
    private String dsAdapter;

    /** 数据源主机/IP */
    private String dsIp;

    /** 数据源端口（字符串，兼容历史数据） */
    private String dsPort;

    /** JDBC 连接 URL；已有值优先保留 */
    private String jdbcUrl;

    /** 连接用户名 */
    private String username;

    /**
     * 连接密码：落库为 {@code ENC:} 前缀的 AES 密文，兼容历史明文。
     * {@link JsonProperty.Access#WRITE_ONLY} 保证只接收、不输出。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /** JDBC 驱动类全限定名 */
    private String driverClass;

    /** 额外连接参数（原样保存） */
    private String connectionProps;

    /** 描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 数据源所属数仓层级：SOURCE/ODS/DWD/DWS/DIM/ADS/DWA 等 */
    private String dataLayer;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 逻辑删除时间，NULL 表示未删除 */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

    // ===== 展示用派生字段（exist=false，不参与持久化） =====
    /** 数据库名/namespace（从 JDBC URL 解析，供前端构建 URL 与表单回显） */
    @TableField(exist = false)
    private String databaseName;

    /** 来源系统（兼容旧前端字段，不在本表持久化） */
    @TableField(exist = false)
    private String sourceSystem;

    /** 是否已设置密码（exist=false，仅供前端展示掩码，不泄露真实密码） */
    @TableField(exist = false)
    private Boolean hasPassword;
}
