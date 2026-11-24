package com.dam.platform.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求（管理端）
 * <p>
 * 各字段不传表示不修改；roleCode 允许修改，但需保持未删除记录内唯一。
 *
 * @author DAM Platform
 */
@Data
public class RoleUpdateRequest {

    /** 角色编码（不传表示不修改） */
    @Size(max = 30, message = "角色编码长度不能超过30")
    private String roleCode;

    /** 角色名称（不传表示不修改） */
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String roleName;

    /** 角色描述（不传表示不修改，传空串表示清空） */
    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

    /** 状态：0-禁用，1-启用（不传表示不修改） */
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;

    /** 数据范围：ALL/DEPARTMENT/SELF（不传表示不修改） */
    @Size(max = 16, message = "数据范围长度不能超过16")
    private String dataScope;

    /** 默认驾驶舱编码（不传表示不修改） */
    @Size(max = 32, message = "默认驾驶舱长度不能超过32")
    private String defaultCockpit;
}
