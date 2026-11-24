package com.dam.platform.common.security;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 服务端数据范围上下文。
 * <p>
 * 登录 token 在签发时写入 dataScope/department/username 声明；各业务服务只从
 * token 读取，不接受前端传入的数据范围，避免“改参数越权”。
 *
 * @author DAM Platform
 */
public final class DataScopeContext {

    /** 全部数据 */
    public static final String ALL = "ALL";
    /** 本部门数据 */
    public static final String DEPARTMENT = "DEPARTMENT";
    /** 仅本人数据 */
    public static final String SELF = "SELF";

    private DataScopeContext() {
    }

    /** 当前请求的数据范围；无 token 摘要时按 ALL 兼容历史内部调用 */
    public static String scope() {
        String value = extra("dataScope");
        if (value == null) return ALL;
        value = value.trim().toUpperCase();
        return ALL.equals(value) || DEPARTMENT.equals(value) || SELF.equals(value) ? value : ALL;
    }

    /** 当前登录用户所属部门，可能为空 */
    public static String department() {
        String value = extra("department");
        return value == null ? "" : value.trim();
    }

    /** 当前登录用户ID，无声明时返回 null */
    public static Long userId() {
        String value = extra("userId");
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    /** 当前登录用户名，可能为空 */
    public static String username() {
        String value = extra("username");
        return value == null ? "" : value.trim();
    }

    /** 是否全院范围 */
    public static boolean isAll() {
        return ALL.equals(scope());
    }

    /** 是否本部门范围 */
    public static boolean isDepartment() {
        return DEPARTMENT.equals(scope());
    }

    /** 是否本人范围 */
    public static boolean isSelf() {
        return SELF.equals(scope());
    }

    private static String extra(String key) {
        try {
            Object value = StpUtil.getExtra(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
