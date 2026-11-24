package com.dam.platform.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.auth.model.pojo.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统角色 Mapper（dam_sys.sys_role）
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供，逻辑删除条件由 {@code @TableLogic} 自动追加。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
