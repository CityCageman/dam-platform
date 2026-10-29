package com.dam.common.vo;

import lombok.Data;

/**
 * 统一 API 响应包装类
 * 所有 REST 接口返回值统一使用本类封装，保证前端接收格式一致：
 * {"code":200,"message":"success","data":...,"timestamp":...}
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {
    /** 响应状态码：200-成功，400-参数错误，401-未登录，403-无权限，500-系统错误 */
    private int code;
    /** 响应消息：成功为 success，失败为具体错误描述 */
    private String message;
    /** 业务数据负载 */
    private T data;
    /** 服务端响应时间戳（毫秒） */
    private long timestamp = System.currentTimeMillis();

    /** 私有构造器：禁止外部直接 new，统一通过静态工厂方法创建 */
    private Result() {}

    /**
     * 成功响应（携带业务数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功结果对象
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 成功响应（无业务数据）
     *
     * @param <T> 数据类型
     * @return 成功结果对象
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应（自定义状态码与消息）
     *
     * @param code    错误状态码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 错误结果对象
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 失败响应（默认 500 系统错误）
     *
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 错误结果对象
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /**
     * 无权限响应（HTTP 403）
     *
     * @param message 无权限提示
     * @param <T>     数据类型
     * @return 403 结果对象
     */
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }

    /**
     * 未认证响应（HTTP 401）
     *
     * @param message 未登录提示
     * @param <T>     数据类型
     * @return 401 结果对象
     */
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }
}
