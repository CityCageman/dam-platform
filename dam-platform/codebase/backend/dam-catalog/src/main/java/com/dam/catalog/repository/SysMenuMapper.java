package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统菜单 Mapper（dam_sys.sys_menu）
 *
 * @author DAM Platform
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
