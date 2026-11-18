package com.dam.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理响应
 * <p>
 * 仅包含可对前端暴露的字段，密码、password_hash、salt 永不出现在响应中。
 *
 * @author DAM Platform
 */
@Data
public class UserResponse {

    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 所属科室/部门 */
    private String department;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 头像地址 */
    private String avatar;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 角色ID列表（仅未删除角色） */
    private List<Long> roleIds = new ArrayList<>();

    /** 角色明细（仅未删除角色） */
    private List<RoleResponse> roles = new ArrayList<>();
}
