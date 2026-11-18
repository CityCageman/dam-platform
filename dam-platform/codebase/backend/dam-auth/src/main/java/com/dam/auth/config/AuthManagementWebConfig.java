package com.dam.auth.config;

import com.dam.auth.service.AuthSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端接口登录拦截配置
 * <p>
 * 在参数绑定/Bean Validation 之前对 /api/auth/users/**、/api/auth/roles/**、/api/auth/menus
 * 统一校验登录态，确保未登录请求即使请求体参数不合法也返回业务码 401（而不是 400）。
 * 网关已经对 /api/** 做 token 校验，这里是服务内二次防护，避免绕过网关直连 dam-auth。
 * OPTIONS 预检请求放行，交由网关/CORS 处理。
 * <p>
 * 当前平台尚未建立 RBAC 管理权限模型；后续增加 ADMIN 权限校验时，应扩展本拦截器。
 *
 * @author DAM Platform
 */
@Configuration
@RequiredArgsConstructor
public class AuthManagementWebConfig implements WebMvcConfigurer {

    /** 登录态支撑组件 */
    private final AuthSupport authSupport;

    /**
     * 注册管理端登录拦截器
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // CORS 预检请求不携带业务 token，直接放行
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                authSupport.requireLogin();
                return true;
            }
        }).addPathPatterns("/api/auth/users/**", "/api/auth/roles/**", "/api/auth/menus");
    }
}
