package com.dam.common.exception;

import com.dam.common.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * 统一拦截所有 Controller 抛出的异常，转换为规范的 Result 结构返回前端
 * 职责：业务异常→对应状态码；参数异常→400；系统异常→500（不泄露内部细节）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理（自定义 BusinessException）
     * 返回异常携带的状态码与消息
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 非法参数异常处理（如类型转换失败）
     * 统一返回 400 参数错误
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * Bean Validation 校验失败处理（@Valid/@Validated）
     * 聚合所有字段错误信息返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        // 收集各字段的校验错误信息，用分号拼接
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验异常: {}", msg);
        return Result.error(400, msg);
    }

    /**
     * 请求体解析异常处理（JSON 格式错误等）
     * 统一返回 400 请求格式错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析异常: {}", e.getMessage());
        return Result.error(400, "请求格式错误");
    }

    /**
     * 静态资源不存在处理（如访问 /、/favicon.ico 等非 API 路径）
     * 纯 API 服务不提供静态资源，返回 404 且不记 ERROR 日志，避免无意义日志刷屏
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getMessage());
        return Result.error(404, "资源不存在");
    }

    /**
     * 兜底异常处理（所有未匹配的异常）
     * 记录完整堆栈日志，对外返回通用 500 消息，避免泄露内部实现
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部异常: ", e);
        return Result.error(500, "系统内部错误，请稍后重试");
    }
}
