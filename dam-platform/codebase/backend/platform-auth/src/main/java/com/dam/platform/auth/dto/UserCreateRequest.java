package com.dam.platform.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * 创建用户请求（管理端）
 * <p>
 * password 为明文入参，服务端使用 BCrypt 加密后写入 sys_user.password；
 * password 字段通过 @ToString.Exclude 避免被误写入日志。
 *
 * @author DAM Platform
 */
@Data
public class UserCreateRequest {

    /** 用户名（未删除记录内唯一） */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    /**
     * 初始密码。
     * <p>
     * 兼容两种入参：明文，或前端使用 /api/auth/public-key 加密后的 RSA 密文（约344字符）。
     * 服务端先解密（解密失败原样按明文处理），再以 BCrypt 加密落库。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 512, message = "密码密文长度不能超过512")
    @ToString.Exclude
    private String password;

    /** 真实姓名 */
    @Size(max = 100, message = "真实姓名长度不能超过100")
    private String realName;

    /** 所属科室/部门（为空时写入“未分配”） */
    @Size(max = 100, message = "部门长度不能超过100")
    private String department;

    /** 邮箱 */
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    /** 手机号 */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    /** 头像地址 */
    @Size(max = 255, message = "头像地址长度不能超过255")
    private String avatar;

    /** 状态：0-禁用，1-启用；为空默认启用 */
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;

    /** 初始角色ID列表（可为空） */
    private List<Long> roleIds;
}
