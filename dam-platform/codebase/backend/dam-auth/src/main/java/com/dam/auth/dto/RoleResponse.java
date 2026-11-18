package com.dam.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色管理响应
 *
 * @author DAM Platform
 */
@Data
public class RoleResponse {

    /** 角色ID */
    private Long id;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

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
}
