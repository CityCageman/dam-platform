package com.dam.platform.portal.controller;

import com.dam.common.vo.Result;
import com.dam.platform.portal.model.PortalDomain;
import com.dam.platform.portal.service.PortalCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一门户接口
 * <p>
 * 提供门户运行状态、能力域目录、能力域详情与首页统计查询。
 * 所有响应统一使用 {@link Result} 包装；能力域不存在时抛出 404 风格业务异常。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    /** 门户目录服务 */
    private final PortalCatalogService portalCatalogService;

    /**
     * 门户运行状态
     *
     * @return 服务名、运行状态与角色
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-portal");
        status.put("status", "UP");
        status.put("role", "unified portal");
        return Result.success(status);
    }

    /**
     * 获取全部能力域（含模块）
     *
     * @return 8 个能力域，各能力域包含其模块列表
     */
    @GetMapping("/domains")
    public Result<List<Map<String, Object>>> domains() {
        List<Map<String, Object>> domains = portalCatalogService.listDomains().stream()
                .map(PortalDomain::toMap)
                .toList();
        return Result.success(domains);
    }

    /**
     * 获取指定能力域详情（含模块）
     *
     * @param code 能力域编码，如 asset、standard
     * @return 能力域详情；不存在时返回 code=404
     */
    @GetMapping("/domains/{code}")
    public Result<Map<String, Object>> domain(@PathVariable String code) {
        return Result.success(portalCatalogService.requireDomain(code).toMap());
    }

    /**
     * 门户首页统计
     *
     * @return domainCount=8、moduleCount=23（由静态目录实时汇总）
     */
    @GetMapping("/home/summary")
    public Result<Map<String, Object>> homeSummary() {
        return Result.success(portalCatalogService.homeSummary());
    }
}
