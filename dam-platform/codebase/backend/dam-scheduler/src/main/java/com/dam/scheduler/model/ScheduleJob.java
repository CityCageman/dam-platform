package com.dam.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度任务模型（骨架）。
 * <p>
 * 仅用于当前阶段的接口示例数据，不对应数据库表；后续接入调度元数据存储时再补充
 * 主键、所属 DAG、依赖关系、重试策略等字段。
 *
 * @author DAM Platform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJob {

    /** 任务编码，全局唯一 */
    private String code;

    /** 任务名称 */
    private String name;

    /** Cron 表达式（Spring 六段式） */
    private String cron;

    /** 任务状态：示例值 DISABLED，真实状态由后续调度引擎维护 */
    private String status;
}
