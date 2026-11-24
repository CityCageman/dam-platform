package com.dam.platform.scheduler.dto.dolphin;

/**
 * 查询 DolphinScheduler 流程实例的结果。
 *
 * @param ok       是否查询成功
 * @param instance DS 流程实例视图，失败时为 {@code null}
 * @param message  结果描述（已脱敏）
 * @author DAM Platform
 */
public record DolphinProcessInstanceResult(boolean ok,
                                           DolphinProcessInstance instance,
                                           String message) {
}
