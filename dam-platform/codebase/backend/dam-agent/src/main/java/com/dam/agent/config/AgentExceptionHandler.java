package com.dam.agent.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.dam.common.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 智能体模块异常处理（补充 dam-common 的全局处理器）
 * <p>
 * 将 sa-token 未登录异常转换为统一的 401 响应。
 */
@Slf4j
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录访问智能体接口: {}", e.getMessage());
        return Result.unauthorized("未登录或token已失效");
    }
}
