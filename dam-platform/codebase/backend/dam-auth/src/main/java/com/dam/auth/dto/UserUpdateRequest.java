package com.dam.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 更新用户请求（管理端）
 * <p>
 * username 仅为兼容前端编辑表单回传的只读字段，服务端完全忽略，用户名创建后不允许修改。
 * password 为空或不传表示不修改密码；非空时按 BCrypt 重新加密。
 * 角色不在本接口修改，统一走 PUT /api/auth/users/{id}/roles。
 *
 * @author DAM Platform
 */
@Data
public class UserUpdateRequest {

    /**
     * 前端编辑表单回传的用户名。
     * <p>
     * 仅用于兼容反序列化，服务端不读取、不写入；用户名创建后不允许修改。
     */
    private String username;

    /** 真实姓名（不传表示不修改，传空串表示清空） */
    @Size(max = 100, message = "真实姓名长度不能超过100")
    private String realName;

    /** 所属科室/部门（不传表示不修改，传空串回填“未分配”） */
    @Size(max = 100, message = "部门长度不能超过100")
    private String department;

    /** 邮箱（不传表示不修改） */
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    /** 手机号（不传表示不修改） */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    /** 头像地址（不传表示不修改） */
    @Size(max = 255, message = "头像地址长度不能超过255")
    private String avatar;

    /** 状态：0-禁用，1-启用（不传表示不修改） */
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;

    /**
     * 新密码（可为空；为空不改密码）。
     * <p>
     * 兼容明文与 RSA 密文，服务端解密后再以 BCrypt 加密落库。
     */
    @Size(max = 512, message = "密码密文长度不能超过512")
    @ToString.Exclude
    private String password;
}
