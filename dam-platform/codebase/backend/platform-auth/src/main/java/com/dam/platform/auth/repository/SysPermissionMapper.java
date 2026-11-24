package com.dam.platform.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.auth.model.pojo.SysPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统权限点 Mapper（dam_sys.sys_permission）
 * <p>
 * 仅用于角色权限查询（可选能力），当前平台尚未基于权限点做接口鉴权。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
