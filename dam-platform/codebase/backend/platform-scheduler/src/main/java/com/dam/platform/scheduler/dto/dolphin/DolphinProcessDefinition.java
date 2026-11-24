package com.dam.platform.scheduler.dto.dolphin;

/**
 * DolphinScheduler 流程定义摘要。
 *
 * @param code         流程定义编码
 * @param name         流程定义名称
 * @param version      流程定义版本
 * @param releaseState 上线状态（DS 原样字符串，如 ONLINE/OFFLINE）
 * @author DAM Platform
 */
public record DolphinProcessDefinition(Long code,
                                       String name,
                                       Integer version,
                                       String releaseState) {
}
