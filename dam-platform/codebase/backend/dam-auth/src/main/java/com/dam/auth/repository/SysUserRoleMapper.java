package com.dam.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.auth.model.pojo.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper（dam_sys.sys_user_role）
 * <p>
 * 关联表不做 {@code @TableLogic}，授权全量替换时物理删除旧关联；
 * 查询处显式过滤 deleted_at，兼容历史逻辑删除数据。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
