package com.dam.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关 Sa-Token 配置
 * 验证请求头中的 token 是否存在，不存在则返回 401
 * token 的有效性由后端各服务自行验证
 */
@Configuration
public class SaTokenConfig {
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                // 登录、获取 RSA 公钥为登录前置接口，无需 token
                .addExclude("/api/auth/login", "/api/auth/public-key", "/favicon.ico")
                .setAuth(obj -> SaRouter.match("/api/**", r -> {
                        // 仅验证 token 是否存在，不做服务端校验
                        // （各服务独立验证 token 有效性）
                        String token = cn.dev33.satoken.stp.StpUtil.getTokenValue();
                        if (token == null || token.isEmpty()) {
                            throw new cn.dev33.satoken.exception.NotLoginException("", "", "未提供token");
                        }
                    }))
                .setError(e -> "{\"code\":401,\"msg\":\"未登录或token已过期\"}");
    }
}
