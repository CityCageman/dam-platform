package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色可查看的资产主题域（dam_sys.sys_role_subject_domain）
 * <p>
 * 未配置任何记录的角色默认可见全部主题域；管理员（admin）始终可见全部。
 */
@Data
@TableName(value = "sys_role_subject_domain", schema = "dam_sys")
public class SysRoleSubjectDomain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID（dam_sys.sys_role.id） */
    private Long roleId;

    /** 主题域ID（asset_category.tree_level=1） */
    private Long subjectDomainId;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
