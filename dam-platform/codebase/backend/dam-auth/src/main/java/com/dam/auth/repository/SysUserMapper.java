package com.dam.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.auth.model.pojo.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper
 * <p>
 * 基于 MyBatis-Plus 提供 sys_user 表的基础 CRUD，
 * 登录/用户信息查询由 {@link com.dam.auth.service.AuthService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
