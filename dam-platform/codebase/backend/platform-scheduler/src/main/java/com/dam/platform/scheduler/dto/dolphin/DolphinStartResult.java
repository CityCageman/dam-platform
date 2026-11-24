package com.dam.platform.scheduler.dto.dolphin;

/**
 * DolphinScheduler 启动流程实例的结果。
 * <p>
 * {@code ok=true} 且 {@code dryRun=false} 时 {@code processInstanceId} 必不为空；
 * 失败时 {@code message} 已脱敏，可直接落库或日志输出。
 *
 * @param ok                是否成功
 * @param processInstanceId DS 流程实例ID，失败或 dry-run 时为 {@code null}
 * @param dryRun            是否为干跑（只做配置/参数校验，不调用 DS 启动接口）
 * @param message           结果描述（已脱敏）
 * @author DAM Platform
 */
public record DolphinStartResult(boolean ok,
                                 Long processInstanceId,
                                 boolean dryRun,
                                 String message) {
}
