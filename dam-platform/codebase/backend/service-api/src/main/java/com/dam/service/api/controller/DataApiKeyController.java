package com.dam.service.api.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.vo.Result;
import com.dam.service.api.dto.DataApiKeyCreateRequest;
import com.dam.service.api.dto.DataApiKeyCreateResult;
import com.dam.service.api.dto.DataApiKeyView;
import com.dam.service.api.service.DataApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据服务 API Key 管理接口
 * <p>
 * 全部接口要求平台登录（Sa-Token），用于为订阅方签发、查询与撤销 OpenAPI
 * 调用凭据。明文 Key 只在创建响应中返回一次，列表与撤销接口均不返回明文或
 * SHA-256 摘要。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/data-service/api-keys")
@RequiredArgsConstructor
public class DataApiKeyController {

    /** API Key 管理服务 */
    private final DataApiKeyService apiKeyService;

    /**
     * 创建 API Key（需平台登录）
     * <p>
     * 请求体：{@code {"name":"...","subscriber":"可选","expiresAt":"可选"}}；
     * {@code subscriber} 为空时默认当前登录用户。响应中的 {@code key} 为明文，
     * 仅返回这一次。
     *
     * @param request 创建请求
     * @return 含明文 Key 的创建结果
     */
    @PostMapping
    public Result<DataApiKeyCreateResult> create(
            @RequestBody(required = false) DataApiKeyCreateRequest request) {
        String loginId = requireLogin();
        return Result.success(apiKeyService.create(request, loginId));
    }

    /**
     * 查询 API Key 列表（需平台登录）
     * <p>
     * 只返回 prefix/name/subscriber/status/expiresAt/lastUsedAt，不返回明文 Key
     * 或摘要。
     *
     * @return API Key 视图列表
     */
    @GetMapping
    public Result<List<DataApiKeyView>> list() {
        String loginId = requireLogin();
        return Result.success(apiKeyService.list(loginId));
    }

    /**
     * 撤销 API Key（需平台登录，幂等）
     *
     * @param id API Key 主键
     * @return 撤销后的 API Key 视图
     */
    @PostMapping("/{id}/revoke")
    public Result<DataApiKeyView> revoke(@PathVariable Long id) {
        String loginId = requireLogin();
        return Result.success(apiKeyService.revoke(id, loginId));
    }

    /**
     * 校验当前请求已登录，并返回登录用户标识
     * <p>
     * 未登录统一转换为 401 业务异常，避免 Sa-Token 异常落到兜底 500 处理。
     *
     * @return 当前登录用户标识
     */
    private static String requireLogin() {
        try {
            StpUtil.checkLogin();
            return StpUtil.getLoginIdAsString();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录");
        }
    }
}
