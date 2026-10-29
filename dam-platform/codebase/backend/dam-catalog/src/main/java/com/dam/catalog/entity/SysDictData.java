package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用字典数据实体
 * <p>
 * 对应表：dam_sys.sys_dict_data
 * 通过 dictTypeId 关联 {@link SysDictType}（字典类型），
 * 每条记录承载一个枚举值（dictValue）及其展示名（dictLabel），
 * 供前端下拉框、表格标签翻译等场景使用。
 *
 * @author DAM Platform
 * @see SysDictType
 */
@Data
@TableName(value = "sys_dict_data", schema = "dam_sys")
public class SysDictData {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典类型ID（关联 sys_dict_type.id） */
    private Integer dictTypeId;

    /** 字典标签（界面展示名称） */
    private String dictLabel;

    /** 字典值（代码中存储的枚举值） */
    private String dictValue;

    /** 扩展值（如样式类名、颜色值等 UI 辅助信息） */
    private String dictExtra;

    /** 同级排序序号 */
    private Integer sortOrder;

    /** 是否默认值（TRUE-是，FALSE-否） */
    private Boolean isDefault;

    /** 备注 */
    private String remarks;

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
