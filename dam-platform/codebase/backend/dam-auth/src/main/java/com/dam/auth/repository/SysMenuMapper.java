package com.dam.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.auth.model.pojo.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统菜单 Mapper（dam_sys.sys_menu）
 * <p>
 * 仅用于角色-菜单授权查询，菜单维护由系统配置模块负责。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
