package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.SysMenu;
import com.dam.catalog.repository.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 系统菜单服务（dam_sys.sys_menu）
 * <p>
 * 读取启用的可见菜单并按 parent_id 构建两级树：
 * 顶级为目录（大模块，menu_type=M），子节点为页面菜单（menu_type=C）。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;

    /**
     * 获取菜单树（仅启用且可见的菜单）
     *
     * @return 菜单树 [{id, menuName, menuType, parentId, path, icon, sortOrder, children}]
     */
    public List<Map<String, Object>> getMenuTree() {
        List<SysMenu> list = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getStatus, 1)
                        .eq(SysMenu::getVisible, 1)
                        .isNull(SysMenu::getDeletedAt)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId));

        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (SysMenu m : list) {
            nodeMap.put(m.getId(), toNode(m));
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysMenu m : list) {
            Map<String, Object> node = nodeMap.get(m.getId());
            if (m.getParentId() != null && nodeMap.containsKey(m.getParentId())) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(m.getParentId()).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /** 菜单实体转树节点 */
    private Map<String, Object> toNode(SysMenu m) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", m.getId());
        n.put("menuName", m.getMenuName());
        n.put("menuType", m.getMenuType());
        n.put("parentId", m.getParentId());
        n.put("path", m.getPath());
        n.put("component", m.getComponent());
        n.put("icon", m.getIcon());
        n.put("sortOrder", m.getSortOrder());
        n.put("children", new ArrayList<Map<String, Object>>());
        return n;
    }
}
