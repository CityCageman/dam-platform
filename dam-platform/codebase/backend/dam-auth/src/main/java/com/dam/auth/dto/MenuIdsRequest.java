package com.dam.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色菜单全量替换请求
 * <p>
 * menuIds 为空数组表示清空该角色全部菜单授权；不传 menuIds 视为参数错误。
 *
 * @author DAM Platform
 */
@Data
public class MenuIdsRequest {

    /** 新的菜单ID列表（全量替换） */
    @NotNull(message = "menuIds 不能为空")
    private List<Long> menuIds;
}
