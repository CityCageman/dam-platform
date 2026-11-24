package com.dam.platform.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户角色全量替换请求
 * <p>
 * roleIds 为空数组表示清空该用户全部角色；不传 roleIds 视为参数错误。
 *
 * @author DAM Platform
 */
@Data
public class RoleIdsRequest {

    /** 新的角色ID列表（全量替换） */
    @NotNull(message = "roleIds 不能为空")
    private List<Long> roleIds;
}
