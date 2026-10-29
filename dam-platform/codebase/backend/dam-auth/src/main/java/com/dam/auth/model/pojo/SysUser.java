package com.dam.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 映射 dam_sys.sys_user 表：用户账号、密码（BCrypt）、基本资料、登录信息
 * 角色由 sys_user_role 关联表承载，本实体仅保留过渡字段 role（不参与持久化）
 */
@Data
@TableName(value = "sys_user", schema = "dam_sys")
public class SysUser {
    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户名（唯一） */
    private String username;
    /** 密码（Bcrypt加密存储） */
    private String password;
    /** 真实姓名 */
    private String realName;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 所属科室/部门 */
    private String department;
    /** 角色（旧字段，新库由 sys_user_role 承载，不参与持久化） */
    @TableField(exist = false)
    private String role;
    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
    /** 最后登录IP */
    private String lastLoginIp;
    /** 状态：0-禁用，1-启用 */
    private Integer status;
    /** 头像地址 */
    private String avatar;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 创建人 */
    private String createdBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 更新人 */
    private String updatedBy;
}
