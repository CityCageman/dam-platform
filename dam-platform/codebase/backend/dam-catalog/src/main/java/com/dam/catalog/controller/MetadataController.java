package com.dam.catalog.controller;

import com.dam.catalog.entity.DamDataSource;
import com.dam.catalog.entity.DamMetadataExt;
import com.dam.catalog.entity.MetadataManagement;
import com.dam.catalog.entity.MetadataSchema;
import com.dam.catalog.service.MetadataAiService;
import com.dam.catalog.service.MetadataService;
import com.dam.catalog.service.TechnicalMetadataService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 元数据管理接口（dam_metadata 模块）
 * <p>
 * 提供：
 * <ul>
 *   <li>资产的技术/业务/关联元数据查询</li>
 *   <li>技术元数据浏览（数据源/表/Schema/表详情/管理元数据保存）</li>
 *   <li>扩展属性（metadata_management.extend_props）的单条/批量保存与删除</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/metadata")
@RequiredArgsConstructor
public class MetadataController {

    /** 元数据服务（技术/业务元数据聚合） */
    private final MetadataService metadataService;

    /** 技术元数据落库服务（表/Schema/管理元数据） */
    private final TechnicalMetadataService technicalMetadataService;

    /** 元数据 AI 智能标注服务（表注释/主题域/安全分级） */
    private final MetadataAiService metadataAiService;

    /**
     * 获取资产的完整技术元数据（6大类别）
     */
    @GetMapping("/technical/{assetId}")
    public Result<Map<String, Object>> getTechnicalMetadata(@PathVariable Long assetId) {
        return Result.success(metadataService.getTechnicalMetadata(assetId));
    }

    /**
     * 获取资产的业务元数据
     */
    @GetMapping("/business/{assetId}")
    public Result<Map<String, Object>> getBusinessMetadata(@PathVariable Long assetId) {
        return Result.success(metadataService.getBusinessMetadata(assetId));
    }

    /**
     * 获取资产的相关元数据（引用/被引用）
     */
    @GetMapping("/related/{assetId}")
    public Result<Map<String, Object>> getRelatedMetadata(@PathVariable Long assetId) {
        return Result.success(metadataService.getRelatedMetadata(assetId));
    }

    // ==================== 技术元数据浏览（dam_metadata 模块） ====================

    /**
     * 查询数据源下的技术元数据表列表（支持按数据源/Schema/关键字过滤）
     */
    @GetMapping("/tables")
    public Result<List<Map<String, Object>>> listDataSourceTables(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String schemaName,
            @RequestParam(required = false) String keyword) {
        return Result.success(technicalMetadataService.listDataSourceTables(dataSourceId, schemaName, keyword));
    }

    /**
     * 查询数据源下的Schema列表
     */
    @GetMapping("/schemas")
    public Result<List<MetadataSchema>> listSchemas(@RequestParam(required = false) Long dataSourceId) {
        return Result.success(technicalMetadataService.listSchemas(dataSourceId));
    }

    /**
     * 元数据层级概况（源库/ODS/DWD/DWS/ADS/DIM 各级的数据源数、表数、字段数）
     */
    @GetMapping("/layer-overview")
    public Result<List<Map<String, Object>>> getLayerOverview() {
        return Result.success(technicalMetadataService.getLayerOverview());
    }

    /**
     * 数据源 Schema 统计（Schema 数量 + 每个 Schema 表数量）
     */
    @GetMapping("/data-source-schema-stats")
    public Result<List<Map<String, Object>>> getDataSourceSchemaStats() {
        return Result.success(technicalMetadataService.getDataSourceSchemaStats());
    }

    /**
     * AI 自动标注（表注释 / 业务主题域 / 数据安全分级）
     * 按主键游标分批处理，前端循环调用直到 hasMore=false，可覆盖数据源下全部表。
     * body: { dataSourceId, pendingOnly, afterId, batchSize }
     * <ul>
     *   <li>pendingOnly=true：仅补标注缺注释/缺主题域/缺安全分级的表</li>
     *   <li>afterId：上一批返回的 nextCursor（首批不传）</li>
     * </ul>
     */
    @PostMapping("/ai-annotate")
    public Result<Map<String, Object>> aiAnnotate(@RequestBody Map<String, Object> body) {
        Long dataSourceId = body.get("dataSourceId") instanceof Number n ? n.longValue() : null;
        Long afterId = body.get("afterId") instanceof Number n ? n.longValue() : null;
        Integer batchSize = body.get("batchSize") instanceof Number n ? n.intValue() : null;
        boolean pendingOnly = Boolean.TRUE.equals(body.get("pendingOnly"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("pendingOnly")));
        String token = cn.dev33.satoken.context.SaHolder.getRequest().getHeader("token");
        return Result.success(metadataAiService.annotate(dataSourceId, pendingOnly, afterId, batchSize, token));
    }

    /**
     * 技术元数据表详情（含字段与管理元数据）
     */
    @GetMapping("/tables/{tableId}")
    public Result<Map<String, Object>> getTableDetail(@PathVariable Long tableId) {
        return Result.success(technicalMetadataService.getTableDetail(tableId));
    }

    /**
     * 保存管理元数据（治理状态/生命周期/安全分级等）
     */
    @PostMapping("/management")
    public Result<MetadataManagement> saveManagementMetadata(@RequestBody MetadataManagement mgmt) {
        return Result.success(technicalMetadataService.saveManagementMetadata(mgmt));
    }

    /**
     * 技术元数据浏览可用的数据源列表
     */
    @GetMapping("/data-sources")
    public Result<List<DamDataSource>> getMetadataDataSources() {
        return Result.success(metadataService.getMetadataDataSources());
    }

    /**
     * 保存单个扩展属性（写入 metadata_management.extend_props JSONB）
     */
    @PostMapping("/ext")
    public Result<DamMetadataExt> saveExtProperty(@RequestBody Map<String, Object> params) {
        Long assetId = ((Number) params.get("assetId")).longValue();
        String category = (String) params.get("category");
        String propKey = (String) params.get("propKey");
        String propValue = (String) params.get("propValue");
        return Result.success(metadataService.saveExtProperty(assetId, category, propKey, propValue));
    }

    /**
     * 批量保存某类别的扩展属性
     */
    @PostMapping("/ext/batch")
    public Result<Void> saveExtProperties(@RequestBody Map<String, Object> params) {
        Long assetId = ((Number) params.get("assetId")).longValue();
        String category = (String) params.get("category");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> props = (List<Map<String, String>>) params.get("properties");
        metadataService.saveExtProperties(assetId, category, props);
        return Result.success();
    }

    /**
     * 删除扩展属性
     */
    @DeleteMapping("/ext/{id}")
    public Result<Void> deleteExtProperty(@PathVariable Long id) {
        metadataService.deleteExtProperty(id);
        return Result.success();
    }
}
