package com.dam.gateway.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关 Sa-Token 配置
 * 校验请求头 JWT 的签名与有效期；无效/过期返回 401
 */
@Configuration
public class SaTokenConfig {

    /**
     * JWT 无状态鉴权逻辑：网关不依赖 SaTokenDao，仅凭统一密钥验签。
     */
    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForStateless();
    }

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                // 登录、获取 RSA 公钥为登录前置接口，无需 token
                .addExclude("/api/auth/login", "/api/auth/public-key", "/favicon.ico")
                .setAuth(obj -> SaRouter.match("/api/**", r -> {
                        // JWT 无状态验签：签名错误、过期、伪造都会抛出 NotLoginException
                        cn.dev33.satoken.stp.StpUtil.checkLogin();
                    }))
                .setError(e -> "{\"code\":401,\"msg\":\"未登录或token已过期\"}");
    }
}
