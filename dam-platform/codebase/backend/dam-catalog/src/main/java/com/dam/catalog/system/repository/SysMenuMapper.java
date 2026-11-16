package com.dam.catalog.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统菜单 Mapper（dam_sys.sys_menu）
 *
 * @author DAM Platform
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
