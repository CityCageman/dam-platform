package com.dam.platform.scheduler.dto.dolphin;

/**
 * DolphinScheduler 流程实例视图。
 * <p>
 * 仅保留调度回写需要的字段；{@code state} 已归一为 DS 3.x 状态码（SUCCESS=7、
 * FAILURE=6 等），原始状态名保留在 {@code stateName}。
 *
 * @param id                        DS 流程实例ID
 * @param name                      流程实例名称
 * @param processDefinitionCode     流程定义编码
 * @param processDefinitionVersion  流程定义版本
 * @param state                     DS 状态码，无法解析时为 {@code null}
 * @param stateName                 DS 原始状态名，可为 {@code null}
 * @param startTime                 DS 开始时间（原样字符串）
 * @param endTime                   DS 结束时间（原样字符串）
 * @author DAM Platform
 */
public record DolphinProcessInstance(Long id,
                                     String name,
                                     Long processDefinitionCode,
                                     Integer processDefinitionVersion,
                                     Integer state,
                                     String stateName,
                                     String startTime,
                                     String endTime) {
}
