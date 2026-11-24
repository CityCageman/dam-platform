package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 映射 dam_sys.sys_user 表：用户账号、密码（BCrypt）、基本资料、登录信息
 * 角色由 sys_user_role 关联表承载，本实体仅保留过渡字段 role（不参与持久化）
 * <p>
 * 密码相关字段禁止返回前端、禁止写入日志（@ToString.Exclude 防止误打日志）；
 * 删除采用 deleted_at 逻辑删除，查询与登录自动排除已删除用户。
 */
@Data
@TableName(value = "sys_user", schema = "dam_sys")
public class SysUser {
    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户名（未删除记录内唯一） */
    private String username;
    /** 密码（Bcrypt加密存储） */
    @ToString.Exclude
    private String password;
    /** 遗留密码哈希列（NOT NULL 占位，与 password 写入同一 BCrypt 值；查询不加载） */
    @TableField(select = false)
    @ToString.Exclude
    private String passwordHash;
    /** 遗留盐值列（NOT NULL 占位，当前写入空串；查询不加载） */
    @TableField(select = false)
    @ToString.Exclude
    private String salt;
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
    /**
     * 删除时间。
     * <p>
     * 逻辑删除：未删除为 NULL，删除时置 now()；deleted_at 为 TIMESTAMP 列，
     * 不能使用默认的 0/1 删除值比较。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
