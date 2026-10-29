package com.dam.auth.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 * 接收前端提交的用户名与密码
 */
@Data
public class LoginRequest {
    /** 用户名 */
    private String username;
    /** 密码（明文，服务端与 BCrypt 哈希比对） */
    private String password;
}
