package com.dam.processing.develop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据开发任务版本快照
 * <p>
 * 对应 {@code dam_dpp.dev_task_version}。任务创建时写入版本 1；后续每次
 * {@code content} 变更都会在同一事务内递增任务主表版本号并追加一条版本记录，
 * 用于追溯脚本内容与变更说明。版本记录不随任务逻辑删除而删除。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "dev_task_version", schema = "dam_dpp")
public class DevTaskVersion {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 ID */
    private Long taskId;

    /** 版本号，从 1 开始，同一任务内唯一 */
    private Integer versionNo;

    /** 该版本的脚本内容快照 */
    private String content;

    /** 变更说明 */
    private String changeNote;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /**
     * 变更说明的只读别名，兼容前端历史版本表格使用的 changeLog 字段。
     *
     * @return 变更说明
     */
    @JsonProperty("changeLog")
    public String getChangeLog() {
        return changeNote;
    }
}
