package com.dam.platform.scheduler.client;

import java.util.Map;

/**
 * DolphinScheduler 3.x 流程实例状态映射。
 * <p>
 * DS 3.x 的 {@code WorkflowExecutionStatus/ExecutionStatus} 状态码在 3.0-3.2 保持稳定：
 * 0 SUBMITTED_SUCCESS、1 RUNNING_EXECUTION、5 STOP、6 FAILURE、7 SUCCESS、
 * 9 KILL、13 FORCED_SUCCESS 等。这里只把终态映射为本地
 * SUCCESS/FAILED，其余（排队、运行中、暂停等）保持不更新，继续轮询。
 * <p>
 * DS 不同小版本的响应可能把 state 序列化为数字或枚举名，因此同时提供
 * 名称→状态码与状态码→名称的兜底转换。
 *
 * @author DAM Platform
 */
public final class DolphinExecutionState {

    /** 已提交，等待调度 */
    public static final int SUBMITTED_SUCCESS = 0;

    /** 正在执行 */
    public static final int RUNNING_EXECUTION = 1;

    /** 停止（用户主动停止） */
    public static final int STOP = 5;

    /** 执行失败 */
    public static final int FAILURE = 6;

    /** 执行成功 */
    public static final int SUCCESS = 7;

    /** 被 kill */
    public static final int KILL = 9;

    /** 强制成功 */
    public static final int FORCED_SUCCESS = 13;

    /** 状态名 → 状态码，兼容 DS 以枚举名返回 state 的版本 */
    private static final Map<String, Integer> CODE_BY_NAME = Map.ofEntries(
            Map.entry("SUBMITTED_SUCCESS", SUBMITTED_SUCCESS),
            Map.entry("RUNNING_EXECUTION", RUNNING_EXECUTION),
            Map.entry("READY_PAUSE", 2),
            Map.entry("PAUSE", 3),
            Map.entry("PAUSED", 3),
            Map.entry("READY_STOP", 4),
            Map.entry("STOP", STOP),
            Map.entry("FAILURE", FAILURE),
            Map.entry("FAILED", FAILURE),
            Map.entry("SUCCESS", SUCCESS),
            Map.entry("NEED_FAULT_TOLERANCE", 8),
            Map.entry("KILL", KILL),
            Map.entry("KILLED", KILL),
            Map.entry("WAITING_THREAD", 10),
            Map.entry("WAITING_DEPEND", 11),
            Map.entry("DELAY_EXECUTION", 12),
            Map.entry("FORCED_SUCCESS", FORCED_SUCCESS),
            Map.entry("SERIAL_WAIT", 14),
            Map.entry("DISPATCH", 15),
            Map.entry("BLOCKED", 16),
            Map.entry("WAITING_GROUP", 17));

    /** 状态码 → 状态名，用于错误/日志描述 */
    private static final Map<Integer, String> NAME_BY_CODE = Map.ofEntries(
            Map.entry(SUBMITTED_SUCCESS, "SUBMITTED_SUCCESS"),
            Map.entry(RUNNING_EXECUTION, "RUNNING_EXECUTION"),
            Map.entry(2, "READY_PAUSE"),
            Map.entry(3, "PAUSE"),
            Map.entry(4, "READY_STOP"),
            Map.entry(STOP, "STOP"),
            Map.entry(FAILURE, "FAILURE"),
            Map.entry(SUCCESS, "SUCCESS"),
            Map.entry(8, "NEED_FAULT_TOLERANCE"),
            Map.entry(KILL, "KILL"),
            Map.entry(10, "WAITING_THREAD"),
            Map.entry(11, "WAITING_DEPEND"),
            Map.entry(12, "DELAY_EXECUTION"),
            Map.entry(FORCED_SUCCESS, "FORCED_SUCCESS"),
            Map.entry(14, "SERIAL_WAIT"),
            Map.entry(15, "DISPATCH"),
            Map.entry(16, "BLOCKED"),
            Map.entry(17, "WAITING_GROUP"));

    /**
     * 工具类禁止实例化。
     */
    private DolphinExecutionState() {
    }

    /**
     * 按状态名解析状态码。
     *
     * @param name DS 状态名，可为空
     * @return 状态码；无法识别时返回 {@code null}
     */
    public static Integer codeByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return CODE_BY_NAME.get(name.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 按状态码取状态名。
     *
     * @param code 状态码
     * @return 状态名；未知状态返回 {@code null}
     */
    public static String nameByCode(Integer code) {
        return code == null ? null : NAME_BY_CODE.get(code);
    }

    /**
     * 把 DS 状态码映射为本地运行终态。
     *
     * @param code DS 状态码
     * @return SUCCESS/FAILED；非终态或未知状态返回 {@code null}
     */
    public static String toLocalStatus(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case SUCCESS, FORCED_SUCCESS -> "SUCCESS";
            case FAILURE, KILL, STOP -> "FAILED";
            default -> null;
        };
    }
}
