package com.dam.platform.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.auth.model.pojo.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper（dam_dpm.sys_role_permission）
 * <p>
 * 仅用于角色权限查询（可选能力）。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
