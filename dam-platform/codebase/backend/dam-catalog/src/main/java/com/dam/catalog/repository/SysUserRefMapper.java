package com.dam.catalog.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户/角色轻量查询（dam_sys 模块），用于解析当前登录用户的角色与可见主题域。
 */
@Mapper
public interface SysUserRefMapper {

    /** 登录用户名（用于识别 admin 管理员） */
    @Select("SELECT username FROM dam_sys.sys_user WHERE id = #{userId} AND deleted_at IS NULL LIMIT 1")
    String selectUsernameById(@Param("userId") Long userId);

    /** 用户的有效角色ID */
    @Select("SELECT role_id FROM dam_sys.sys_user_role WHERE user_id = #{userId} AND status = 1 AND deleted_at IS NULL")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 启用角色列表（用于配置角色可见主题域） */
    @Select("SELECT id, role_code AS \"roleCode\", role_name AS \"roleName\" FROM dam_sys.sys_role "
            + "WHERE status = 1 AND deleted_at IS NULL ORDER BY id")
    List<java.util.Map<String, Object>> selectRoles();
}
