package com.dam.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建角色请求（管理端）
 *
 * @author DAM Platform
 */
@Data
public class RoleCreateRequest {

    /** 角色编码（未删除记录内唯一） */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 30, message = "角色编码长度不能超过30")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String roleName;

    /** 角色描述 */
    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

    /** 状态：0-禁用，1-启用；为空默认启用 */
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;
}
