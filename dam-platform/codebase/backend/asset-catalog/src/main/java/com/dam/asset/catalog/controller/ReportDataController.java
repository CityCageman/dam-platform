package com.dam.asset.catalog.controller;

import com.dam.asset.catalog.entity.DamAsset;
import com.dam.asset.catalog.service.ReportDataService;
import com.dam.asset.metadata.entity.DamDataSource;
import com.dam.asset.metadata.repository.DamDataSourceMapper;
import com.dam.asset.metric.entity.MetricDefinition;
import com.dam.platform.common.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 报表服务使用的 DAM 数据接口，避免报表直接依赖 DAM Mapper。 */
@RestController
@RequestMapping("/api/catalog/report-data")
public class ReportDataController {

    private final DamDataSourceMapper dataSourceMapper;
    private final ReportDataService reportDataService;

    public ReportDataController(DamDataSourceMapper dataSourceMapper, ReportDataService reportDataService) {
        this.dataSourceMapper = dataSourceMapper;
        this.reportDataService = reportDataService;
    }

    // ==================== 数据源 / 资产注释 ====================

    @GetMapping("/data-source/{id}")
    public Result<Map<String, Object>> dataSource(@PathVariable Long id) {
        DamDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) return Result.error("数据源不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ds.getId());
        m.put("dsName", ds.getDsName());
        m.put("dsType", ds.getDsType());
        m.put("jdbcUrl", ds.getJdbcUrl());
        m.put("username", ds.getUsername());
        m.put("password", ds.getPassword());
        m.put("status", ds.getStatus());
        return Result.success(m);
    }

    @GetMapping("/asset-comments")
    public Result<Map<String, String>> assetComments(@RequestParam Long dataSourceId) {
        List<DamAsset> assets = reportDataService.assetsByDataSource(dataSourceId);
        return Result.success(assets.stream().filter(a -> a.getDescription() != null)
                .collect(java.util.stream.Collectors.toMap(DamAsset::getAssetName, DamAsset::getDescription, (a, b) -> a)));
    }

    // ==================== 主题域 / 分类 ====================

    /** 当前角色可见主题域（报表卡片、详情可见性收敛） */
    @GetMapping("/visible-domains")
    public Result<Map<String, Object>> visibleDomains() {
        return Result.success(reportDataService.visibleDomains());
    }

    /** 全部启用的一级主题域（报表维度值） */
    @GetMapping("/domains")
    public Result<List<Map<String, Object>>> domains() {
        return Result.success(reportDataService.domains());
    }

    /** 按编码查询分类（报表保存的“未分类”兜底） */
    @GetMapping("/category-by-code")
    public Result<Map<String, Object>> categoryByCode(@RequestParam String code) {
        return Result.success(reportDataService.categoryByCode(code));
    }

    // ==================== 资产 ====================

    /** 按ID批量查询资产（报表卡片/详情展示） */
    @GetMapping("/assets")
    public Result<List<DamAsset>> assets(@RequestParam String ids) {
        return Result.success(reportDataService.assets(parseIds(ids)));
    }

    /** 按ID查询单个资产 */
    @GetMapping("/assets/{id}")
    public Result<DamAsset> asset(@PathVariable Long id) {
        return Result.success(reportDataService.asset(id));
    }

    /** 报表资产新建/更新（报表即资产） */
    @PostMapping("/assets/upsert")
    public Result<DamAsset> upsertAsset(@RequestBody Map<String, Object> body) {
        return Result.success(reportDataService.upsertAsset(body));
    }

    /** 报表删除时同步逻辑删除报表资产 */
    @PostMapping("/assets/{id}/delete")
    public Result<Void> deleteAsset(@PathVariable Long id) {
        reportDataService.softDeleteAsset(id);
        return Result.success();
    }

    // ==================== 资产分类归属 ====================

    /** 资产的多主题域归属（报表详情） */
    @GetMapping("/asset-categories/{assetId}")
    public Result<List<Map<String, Object>>> assetCategories(@PathVariable Long assetId) {
        return Result.success(reportDataService.assetCategories(assetId));
    }

    /** 资产直接关联的主题域ID（报表可见性校验） */
    @GetMapping("/asset-domain-ids/{assetId}")
    public Result<List<Long>> assetDomainIds(@PathVariable Long assetId) {
        return Result.success(reportDataService.assetDomainIds(assetId));
    }

    /** 指定主题域下的资产ID（报表卡片按主题域过滤，避免报表直连 DAM 表） */
    @GetMapping("/asset-ids-for-domains")
    public Result<List<Long>> assetIdsForDomains(@RequestParam String domainIds) {
        return Result.success(reportDataService.assetIdsForDomains(parseIds(domainIds)));
    }

    /** 批量资产分类路径（报表卡片展示，避免 N+1） */
    @GetMapping("/asset-category-paths")
    public Result<Map<Long, Map<String, Object>>> assetCategoryPaths(@RequestParam String assetIds) {
        return Result.success(reportDataService.assetCategoryPaths(parseIds(assetIds)));
    }

    /** 保存资产在主题域下的类目归属 */
    @PostMapping("/asset-categories")
    public Result<Void> saveAssetCategory(@RequestBody Map<String, Object> body) {
        reportDataService.saveAssetCategory(longVal(body.get("assetId")),
                longVal(body.get("subjectDomainId")), longVal(body.get("categoryId")));
        return Result.success();
    }

    // ==================== 技术元数据 / 指标 / 知识库 ====================

    /** 指定数据源下已同步的表结构与字段（报表 SQL 生成的 Prompt 上下文） */
    @GetMapping("/tables")
    public Result<List<Map<String, Object>>> tables(@RequestParam Long dataSourceId) {
        return Result.success(reportDataService.tables(dataSourceId));
    }

    /** 指定数据源下启用指标（报表 Prompt 口径参考） */
    @GetMapping("/metrics")
    public Result<List<MetricDefinition>> metrics(@RequestParam Long dataSourceId) {
        return Result.success(reportDataService.metrics(dataSourceId));
    }

    /** 检索业务知识库（报表 Prompt 口径参考） */
    @PostMapping("/knowledge/search")
    public Result<Map<String, Object>> searchKnowledge(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? null : String.valueOf(body.get("query"));
        int limit = body.get("limit") instanceof Number n ? n.intValue() : 5;
        return Result.success(reportDataService.searchKnowledge(query, limit));
    }

    // ==================== 内部工具 ====================

    private List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) return ids;
        for (String part : raw.split(",")) {
            Long id = longVal(part);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private Long longVal(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v != null && !String.valueOf(v).isBlank()) {
            try {
                return Long.valueOf(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
