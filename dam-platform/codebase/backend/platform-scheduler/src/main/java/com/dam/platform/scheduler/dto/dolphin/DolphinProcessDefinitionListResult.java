package com.dam.platform.scheduler.dto.dolphin;

import java.util.List;

/**
 * 查询 DolphinScheduler 流程定义列表的结果。
 *
 * @param ok          是否查询成功
 * @param total       DS 返回的总数，缺失时取列表长度
 * @param definitions 流程定义摘要列表
 * @param message     结果描述（已脱敏）
 * @author DAM Platform
 */
public record DolphinProcessDefinitionListResult(boolean ok,
                                                 int total,
                                                 List<DolphinProcessDefinition> definitions,
                                                 String message) {
}
