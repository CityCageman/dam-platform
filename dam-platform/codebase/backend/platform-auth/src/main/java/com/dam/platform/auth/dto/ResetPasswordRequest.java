package com.dam.platform.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 重置密码请求（管理端）
 * <p>
 * 主字段为 newPassword（任务约定）；同时兼容前端已有的 {password: ...} 入参。
 * 两种字段均支持明文或 RSA 密文（前端使用 /api/auth/public-key 加密），
 * 服务端先解密（解密失败按明文处理），再用 BCrypt 加密落库；不返回、不写日志。
 *
 * @author DAM Platform
 */
@Data
public class ResetPasswordRequest {

    /** 新密码（任务约定字段） */
    @Size(max = 512, message = "新密码密文长度不能超过512")
    @ToString.Exclude
    private String newPassword;

    /** 新密码（兼容前端 {password: ...} 字段） */
    @Size(max = 512, message = "新密码密文长度不能超过512")
    @ToString.Exclude
    private String password;

    /**
     * 取实际生效的密码入参（优先 newPassword，其次 password）
     *
     * @return 明文或 RSA 密文，均可能为 null
     */
    public String effectivePassword() {
        if (newPassword != null && !newPassword.isBlank()) {
            return newPassword;
        }
        return password;
    }
}
