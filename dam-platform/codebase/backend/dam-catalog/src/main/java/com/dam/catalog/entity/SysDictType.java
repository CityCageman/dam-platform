package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用字典类型实体
 * <p>
 * 对应表：dam_sys.sys_dict_type
 * 用于管理系统内所有"枚举值翻译类"字典的类型定义（如资产类型、治理状态、工单状态等），
 * 与 {@link SysDictData}（字典数据）组成一对多的通用字典体系。
 * <p>
 * 业务约定：枚举值翻译类数据（状态码、操作类型等）统一使用本表承载，
 * 系统配置类数据（导航、安全等级等）保留在 sys_config 表中，两者职责分离。
 *
 * @author DAM Platform
 * @see SysDictData
 */
@Data
@TableName(value = "sys_dict_type", schema = "dam_sys")
public class SysDictType {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型编码（程序枚举引用，如 asset_type、governance_status） */
    private String dictTypeCode;

    /** 字典类型名称（如：资产类型、治理状态） */
    private String dictTypeName;

    /** 备注（说明该字典被哪些表的字段引用） */
    private String remark;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 删除时间（软删除标记） */
    private LocalDateTime deletedAt;
}
