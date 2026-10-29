package com.dam.auth.controller;

import com.dam.auth.dto.LoginRequest;
import com.dam.auth.service.AuthService;
import com.dam.auth.service.CryptoService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口
 * 提供登录、登出、当前用户信息查询
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 认证服务 */
    private final AuthService authService;

    /** 密码加解密服务 */
    private final CryptoService cryptoService;

    /**
     * 获取登录密码加密使用的 RSA 公钥
     */
    @GetMapping("/public-key")
    public Result<String> getPublicKey() {
        return Result.success(cryptoService.getPublicKey());
    }

    /**
     * 用户登录
     *
     * @param req 登录请求（用户名+密码）
     * @return token 与用户信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return authService.logout();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser() {
        return authService.getCurrentUser();
    }
}
