package com.dam.platform.scheduler.dto.dolphin;

/**
 * DolphinScheduler 连通性探测结果。
 *
 * @param ok              是否可用
 * @param message         结果描述（已脱敏）
 * @param definitionCount 拉取到的流程定义数量，失败时为 {@code null}
 * @author DAM Platform
 */
public record DolphinProbeResult(boolean ok,
                                 String message,
                                 Integer definitionCount) {
}
