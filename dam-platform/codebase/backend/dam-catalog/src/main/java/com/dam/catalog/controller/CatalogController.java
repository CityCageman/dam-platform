package com.dam.catalog.controller;

import com.dam.catalog.entity.*;
import com.dam.catalog.service.AssetClassifyAiService;
import com.dam.catalog.service.CatalogService;
import com.dam.catalog.service.ConfigService;
import com.dam.catalog.service.CryptoService;
import com.dam.catalog.service.DataSourceTypeService;
import com.dam.catalog.service.DictService;
import com.dam.catalog.service.DiscoveryService;
import com.dam.catalog.service.ImportService;
import com.dam.catalog.service.TechnicalMetadataService;
import com.dam.catalog.service.VersionService;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据目录（Catalog）接口
 * 提供分类、资产、数据源扫描、字段同步、批量导入等 REST 接口
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final DiscoveryService discoveryService;
    private final ImportService importService;
    private final VersionService versionService;
    private final ConfigService configService;
    private final DataSourceTypeService dataSourceTypeService;
    private final TechnicalMetadataService technicalMetadataService;
    private final DictService dictService;
    private final CryptoService cryptoService;
    private final AssetClassifyAiService assetClassifyAiService;

    // ==================== 分类接口 ====================

    /**
     * 获取分类标准类型列表
     */
    @GetMapping("/category-types")
    public Result<List<Map<String, String>>> getCategoryTypes() {
        return Result.success(catalogService.getCategoryTypes());
    }

    /**
     * 获取分类树（可按类型过滤）
     *
     * @param type 分类类型（分类ID）
     */
    @GetMapping("/categories")
    public Result<List<DamCategory>> getCategories(@RequestParam(required = false) String type) {
        return Result.success(catalogService.getCategoryTree(type));
    }

    /**
     * 新增分类
     */
    @PostMapping("/categories")
    public Result<DamCategory> createCategory(@RequestBody DamCategory category) {
        return Result.success(catalogService.createCategory(category));
    }

    /**
     * 更新分类
     */
    @PutMapping("/categories")
    public Result<DamCategory> updateCategory(@RequestBody DamCategory category) {
        return Result.success(catalogService.updateCategory(category));
    }

    /**
     * 删除分类（含子分类级联处理）
     */
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return Result.success();
    }

    // ==================== 资产接口 ====================

    /**
     * 分页搜索资产
     * 支持关键词/类型/分类/密级/标签/数据库名过滤与排序
     */
    @GetMapping("/assets/search")
    public Result<PageResult<DamAsset>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String securityLevel,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String subjectDomain,
            @RequestParam(required = false) Long subjectDomainId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(defaultValue = "false") boolean enforceDomainPermission) {
        return Result.success(catalogService.searchAssets(keyword, assetType, categoryId, securityLevel, tagId, databaseName, dataSourceId, subjectDomain, subjectDomainId, sortBy, page, pageSize, enforceDomainPermission));
    }

    /**
     * 获取资产详情（含字段、标签、评分、收藏状态、使用记录）
     */
    @GetMapping("/assets/{id}")
    public Result<Map<String, Object>> getAssetDetail(@PathVariable Long id) {
        return Result.success(catalogService.getAssetDetail(id));
    }

    /**
     * 新增资产
     */
    @PostMapping("/assets")
    public Result<DamAsset> createAsset(@RequestBody DamAsset asset) {
        return Result.success(catalogService.createAsset(asset));
    }

    /**
     * 更新资产
     */
    @PutMapping("/assets")
    public Result<DamAsset> updateAsset(@RequestBody DamAsset asset) {
        return Result.success(catalogService.updateAsset(asset));
    }

    /**
     * 资产主题域-类目树（按当前登录用户角色过滤可见主题域）
     */
    @GetMapping("/category-domains")
    public Result<List<DamCategory>> listVisibleCategoryDomains() {
        return Result.success(catalogService.listVisibleCategoryDomains());
    }

    /**
     * 全部主题域-类目（分类管理与维护用，不按角色过滤）
     */
    @GetMapping("/category-domains/all")
    public Result<List<DamCategory>> listAllCategoryDomains() {
        return Result.success(catalogService.listCategoryDomains());
    }

    /**
     * 查询资产的多主题域归属
     */
    @GetMapping("/assets/{assetId}/categories")
    public Result<List<Map<String, Object>>> listAssetCategories(@PathVariable Long assetId) {
        return Result.success(catalogService.listAssetCategories(assetId));
    }

    /**
     * 设置资产在某主题域下的类目（同一主题域只保留一个类目）
     * body: { subjectDomainId, categoryId }，categoryId 为空表示取消该主题域归属
     */
    @PutMapping("/assets/{assetId}/categories")
    public Result<Void> saveAssetCategory(@PathVariable Long assetId, @RequestBody Map<String, Object> body) {
        Long subjectDomainId = body.get("subjectDomainId") instanceof Number n ? n.longValue() : null;
        Long categoryId = body.get("categoryId") instanceof Number n ? n.longValue() : null;
        catalogService.saveAssetCategory(assetId, subjectDomainId, categoryId);
        return Result.success();
    }

    /**
     * 启用角色列表（用于配置角色可见主题域）
     */
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> listRoles() {
        return Result.success(catalogService.listRoles());
    }

    /**
     * 角色的可见主题域
     */
    @GetMapping("/roles/{roleId}/subject-domains")
    public Result<List<Long>> listRoleSubjectDomains(@PathVariable Long roleId) {
        return Result.success(catalogService.listRoleSubjectDomainIds(roleId));
    }

    /**
     * 保存角色可见主题域（全量覆盖），body: { subjectDomainIds: [] }
     */
    @PutMapping("/roles/{roleId}/subject-domains")
    @SuppressWarnings("unchecked")
    public Result<Void> saveRoleSubjectDomains(@PathVariable Long roleId, @RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object raw = body.get("subjectDomainIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) ids.add(n.longValue());
                else if (o != null) ids.add(Long.valueOf(String.valueOf(o)));
            }
        }
        catalogService.saveRoleSubjectDomains(roleId, ids);
        return Result.success();
    }

    /**
     * 删除资产
     */
    @DeleteMapping("/assets/{id}")
    public Result<Void> deleteAsset(@PathVariable Long id) {
        catalogService.deleteAsset(id);
        return Result.success();
    }

    // ==================== 批量导入 ====================

    /**
     * CSV 导入资产
     */
    @PostMapping("/assets/import/csv")
    public Result<List<String>> importCsv(@RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String sourceSystem) {
        return Result.success(importService.importFromCsv(file, categoryId, sourceSystem));
    }

    /**
     * Excel 导入资产
     */
    @PostMapping("/assets/import/excel")
    public Result<List<String>> importExcel(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String sourceSystem) {
        return Result.success(importService.importFromExcel(file, categoryId, sourceSystem));
    }

    // ==================== 安全等级重新计算 ====================

    /**
     * 重新计算资产安全等级
     * 传 assetId 计算单个；不传则全量重算
     */
    @PostMapping("/assets/recalc-security")
    public Result<Map<String, Object>> recalculateSecurityLevels(@RequestBody(required = false) Map<String, Object> params) {
        if (params != null && params.containsKey("assetId")) {
            Long assetId = ((Number) params.get("assetId")).longValue();
            catalogService.calculateAssetSecurityLevel(assetId);
            return Result.success(Map.of("assetId", assetId, "message", "已重新计算"));
        } else {
            int count = catalogService.recalculateAllSecurityLevels();
            return Result.success(Map.of("updatedCount", count));
        }
    }

    // ==================== AI 智能识别资产分类 ====================

    /**
     * AI 智能识别资产分类（扫描同步到资产目录后调用）
     * 按资产主键游标分批，前端循环调用直到 hasMore=false。
     * body: { dataSourceId, pendingOnly, afterId, batchSize }
     * <ul>
     *   <li>pendingOnly=true：仅处理当前处于“未分类”的资产</li>
     *   <li>afterId：上一批返回的 nextCursor（首批不传）</li>
     * </ul>
     */
    @PostMapping("/assets/ai-classify")
    public Result<Map<String, Object>> aiClassifyAssets(@RequestBody Map<String, Object> body) {
        Long dataSourceId = body.get("dataSourceId") instanceof Number n ? n.longValue() : null;
        Long afterId = body.get("afterId") instanceof Number n ? n.longValue() : null;
        Integer batchSize = body.get("batchSize") instanceof Number n ? n.intValue() : null;
        boolean pendingOnly = Boolean.TRUE.equals(body.get("pendingOnly"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("pendingOnly")));
        String token = cn.dev33.satoken.context.SaHolder.getRequest().getHeader("token");
        return Result.success(assetClassifyAiService.classify(dataSourceId, pendingOnly, afterId, batchSize, token));
    }

    // ==================== 记录数刷新 ====================

    /**
     * 刷新资产记录数（估算法）
     * 传 assetId 刷新单个；不传则全量刷新
     */
    @PostMapping("/assets/refresh-counts")
    public Result<Map<String, Object>> refreshRecordCounts(@RequestBody(required = false) Map<String, Object> params) {
        if (params != null && params.containsKey("assetId")) {
            Long assetId = ((Number) params.get("assetId")).longValue();
            long count = catalogService.refreshRecordCount(assetId);
            return Result.success(Map.of("assetId", assetId, "recordCount", count, "type", "estimate"));
        } else {
            int updated = catalogService.refreshAllRecordCounts();
            return Result.success(Map.of("updatedCount", updated));
        }
    }

    /**
     * 获取资产精确记录数（实时统计）
     */
    @PostMapping("/assets/exact-count/{assetId}")
    public Result<Map<String, Object>> getExactRecordCount(@PathVariable Long assetId) {
        long count = catalogService.getExactRecordCount(assetId);
        return Result.success(Map.of("assetId", assetId, "recordCount", count));
    }

    // ==================== 自动发现 ====================

    /**
     * 获取前端密码加密使用的 RSA 公钥
     */
    @GetMapping("/discovery/public-key")
    public Result<String> getPublicKey() {
        return Result.success(cryptoService.getPublicKey());
    }

    /**
     * 扫描数据源（自动发现表与字段）
     * <p>传 dataSourceId 时由后端安全读取凭据；否则按参数连接（password 为 RSA 密文）</p>
     *
     * @param params 数据源连接参数（dataSourceId 或 host/port/databaseName/username/password/sourceType/schemas）
     */
    @PostMapping("/discovery/scan")
    public Result<Map<String, Object>> scanDataSource(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<String> schemas = (List<String>) params.get("schemas");
        Number dataSourceId = (Number) params.get("dataSourceId");
        if (dataSourceId == null) {
            throw new BusinessException("请传 dataSourceId，数据源凭据由后端安全读取");
        }
        // 凭据由后端按ID安全读取，前端不传密码
        return Result.success(discoveryService.scanDataSource(dataSourceId.longValue(), schemas));
    }

    /**
     * 将 Object 参数安全转为 String
     */
    private static String toString(Object obj) {
        if (obj == null) return null;
        return obj.toString();
    }

    /**
     * 按需同步单表字段
     */
    @PostMapping("/discovery/sync-fields")
    public Result<Map<String, Object>> syncTableFields(@RequestBody Map<String, Object> params) {
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        Long assetId = params.get("assetId") != null
                ? ((Number) params.get("assetId")).longValue() : null;
        String schemaName = (String) params.get("schemaName");
        String tableName = (String) params.get("tableName");
        if (dataSourceId == null || tableName == null) {
            throw new BusinessException("参数不完整");
        }
        return Result.success(discoveryService.syncTableFields(dataSourceId, schemaName, tableName, assetId));
    }

    /**
     * 确认同步字段变更
     */
    @PostMapping("/discovery/sync-fields/confirm")
    public Result<Void> confirmSyncFields(@RequestBody Map<String, Object> params) {
        Long assetId = params.get("assetId") != null
                ? ((Number) params.get("assetId")).longValue() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) params.get("columns");
        if (assetId == null || columns == null) {
            throw new BusinessException("参数不完整");
        }
        discoveryService.confirmSyncFields(assetId, columns);
        return Result.success();
    }

    /**
     * 获取数据源的 schema 列表（先拉 schema，再按需扫描）
     */
    @PostMapping("/discovery/schemas")
    public Result<List<String>> listSchemas(@RequestBody Map<String, Object> params) {
        Number dataSourceId = (Number) params.get("dataSourceId");
        if (dataSourceId == null) {
            throw new BusinessException("请传 dataSourceId，数据源凭据由后端安全读取");
        }
        // 凭据由后端按ID安全读取，前端不传密码
        return Result.success(discoveryService.listSchemas(dataSourceId.longValue()));
    }

    @PostMapping("/discovery/register")
    public Result<List<DamAsset>> batchRegister(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) params.get("tables");
        String sourceSystem = (String) params.get("sourceSystem");
        String databaseName = (String) params.get("databaseName");
        String securityLevel = (String) params.get("securityLevel");
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;

        List<DamAsset> assets = discoveryService.batchRegister(tables, sourceSystem, databaseName, securityLevel,
                (String) params.get("scanId"), dataSourceId);

        return Result.success(assets);
    }

    // ==================== 字段接口 ====================

    @GetMapping("/assets/{assetId}/fields")
    public Result<List<DamField>> getFields(@PathVariable Long assetId) {
        return Result.success(catalogService.getFieldsByAssetId(assetId));
    }

    /**
     * 获取字段中文注释字典（字段名 → 已有中文注释），供 AI 生成注释时做关联联想
     */
    @GetMapping("/fields/comment-dict")
    public Result<Map<String, String>> getFieldCommentDict() {
        return Result.success(catalogService.getFieldCommentDict());
    }

    /**
     * 更新字段信息（手动补充注释、默认值、长度、可空、主键等）
     */
    @PutMapping("/fields")
    public Result<DamField> updateField(@RequestBody DamField field) {
        return Result.success(catalogService.updateField(field));
    }

    // ==================== 标签接口 ====================

    @GetMapping("/tags")
    public Result<List<DamTag>> getAllTags() {
        return Result.success(catalogService.getAllTags());
    }

    @PostMapping("/tags")
    public Result<DamTag> createTag(@RequestBody DamTag tag) {
        return Result.success(catalogService.createTag(tag));
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        catalogService.deleteTag(id);
        return Result.success();
    }

    @PostMapping("/assets/{assetId}/tags")
    public Result<Void> assignTag(@PathVariable Long assetId, @RequestBody Map<String, Long> body) {
        catalogService.assignTag(assetId, body.get("tagId"));
        return Result.success();
    }

    @DeleteMapping("/assets/{assetId}/tags/{tagId}")
    public Result<Void> removeTag(@PathVariable Long assetId, @PathVariable Long tagId) {
        catalogService.removeTag(assetId, tagId);
        return Result.success();
    }

    @GetMapping("/assets/{assetId}/tags")
    public Result<List<DamTag>> getAssetTags(@PathVariable Long assetId) {
        return Result.success(catalogService.getTagsByAssetId(assetId));
    }

    // ==================== 收藏接口 ====================

    @PostMapping("/favorites/{assetId}")
    public Result<Void> addFavorite(@PathVariable Long assetId) {
        catalogService.addFavorite(assetId);
        return Result.success();
    }

    @DeleteMapping("/favorites/{assetId}")
    public Result<Void> removeFavorite(@PathVariable Long assetId) {
        catalogService.removeFavorite(assetId);
        return Result.success();
    }

    @GetMapping("/favorites")
    public Result<List<DamAsset>> getMyFavorites() {
        return Result.success(catalogService.getMyFavorites());
    }

    // ==================== 评价接口 ====================

    @PostMapping("/ratings")
    public Result<DamRating> rateAsset(@RequestBody DamRating rating) {
        return Result.success(catalogService.rateAsset(rating));
    }

    @GetMapping("/assets/{assetId}/ratings")
    public Result<List<DamRating>> getAssetRatings(@PathVariable Long assetId) {
        return Result.success(catalogService.getAssetRatings(assetId));
    }

    // ==================== 元数据版本接口 ====================

    @GetMapping("/assets/{assetId}/versions")
    public Result<List<DamMetadataVersion>> getVersions(@PathVariable Long assetId) {
        return Result.success(versionService.getVersions(assetId));
    }

    @GetMapping("/assets/{assetId}/versions/compare")
    public Result<Map<String, Object>> compareVersions(
            @PathVariable Long assetId,
            @RequestParam Integer versionA,
            @RequestParam Integer versionB) {
        return Result.success(versionService.compareVersions(assetId, versionA, versionB));
    }

    // ==================== 元数据采集接口 ====================

    @GetMapping("/collections")
    public Result<List<DamMetadataCollection>> getCollections(@RequestParam(required = false) String sourceType) {
        return Result.success(catalogService.getCollections(sourceType));
    }

    @PostMapping("/collections")
    public Result<DamMetadataCollection> createCollection(@RequestBody DamMetadataCollection collection) {
        return Result.success(catalogService.createCollection(collection));
    }

    @GetMapping("/collections/{id}")
    public Result<Map<String, Object>> getCollectionDetail(@PathVariable Long id) {
        return Result.success(catalogService.getCollectionDetail(id));
    }

    @GetMapping("/collections/compare/{idA}/{idB}")
    public Result<Map<String, Object>> compareCollections(@PathVariable Long idA, @PathVariable Long idB) {
        return Result.success(catalogService.compareCollections(idA, idB));
    }

    // ==================== 来源系统管理 ====================

    @GetMapping("/source-systems")
    public Result<List<DamSourceSystem>> getSourceSystems() {
        return Result.success(catalogService.getSourceSystems());
    }

    @PostMapping("/source-systems")
    public Result<DamSourceSystem> createSourceSystem(@RequestBody DamSourceSystem sys) {
        return Result.success(catalogService.createSourceSystem(sys));
    }

    @PutMapping("/source-systems")
    public Result<DamSourceSystem> updateSourceSystem(@RequestBody DamSourceSystem sys) {
        return Result.success(catalogService.updateSourceSystem(sys));
    }

    @DeleteMapping("/source-systems/{id}")
    public Result<Void> deleteSourceSystem(@PathVariable Long id) {
        catalogService.deleteSourceSystem(id);
        return Result.success();
    }

    // ==================== 数据源管理 ====================

    @GetMapping("/data-sources")
    public Result<List<DamDataSource>> getDataSources() {
        return Result.success(catalogService.getDataSources());
    }

    @GetMapping("/data-sources/{id}")
    public Result<Map<String, Object>> getDataSourceDetail(@PathVariable Long id) {
        return Result.success(catalogService.getDataSourceDetail(id));
    }

    @PostMapping("/data-sources")
    public Result<DamDataSource> createDataSource(@RequestBody DamDataSource ds) {
        return Result.success(catalogService.createDataSource(ds));
    }

    @PutMapping("/data-sources")
    public Result<DamDataSource> updateDataSource(@RequestBody DamDataSource ds) {
        return Result.success(catalogService.updateDataSource(ds));
    }

    @DeleteMapping("/data-sources/{id}")
    public Result<Void> deleteDataSource(@PathVariable Long id) {
        catalogService.deleteDataSource(id);
        return Result.success();
    }

    @PostMapping("/data-sources/test-connection")
    public Result<Void> testConnection(@RequestBody Map<String, Object> params) {
        Number dataSourceId = (Number) params.get("dataSourceId");
        if (dataSourceId != null) {
            // 编辑场景：凭据由后端按ID安全读取，前端无需回传密码
            discoveryService.testConnection(dataSourceId.longValue());
            return Result.success();
        }
        discoveryService.testConnection(
                toString(params.get("dsType")),
                toString(params.get("dsIp")),
                Integer.parseInt(toString(params.getOrDefault("dsPort", "3306"))),
                toString(params.get("databaseName")),
                toString(params.get("username")),
                toString(params.get("password")));
        return Result.success();
    }

    // ==================== 数据源类型管理 ====================

    @GetMapping("/data-source-types")
    public Result<List<DamDataSourceType>> getDataSourceTypes() {
        return Result.success(discoveryService.getDataSourceTypeList());
    }

    @PostMapping("/data-source-types")
    public Result<DamDataSourceType> createDataSourceType(@RequestBody DamDataSourceType type) {
        return Result.success(dataSourceTypeService.create(type));
    }

    @PutMapping("/data-source-types")
    public Result<DamDataSourceType> updateDataSourceType(@RequestBody DamDataSourceType type) {
        return Result.success(dataSourceTypeService.update(type));
    }

    @DeleteMapping("/data-source-types/{id}")
    public Result<Void> deleteDataSourceType(@PathVariable Long id) {
        dataSourceTypeService.delete(id);
        return Result.success();
    }

    @GetMapping("/data-source-types/select")
    public Result<List<Map<String, String>>> getDataSourceTypeSelect() {
        return Result.success(discoveryService.getSupportedTypes());
    }

    // ==================== 基础配置 ====================

    @GetMapping("/config/{configType}")
    public Result<List<DamConfig>> getConfig(@PathVariable String configType) {
        return Result.success(configService.getConfigByType(configType));
    }

    @GetMapping("/config/{configType}/tree")
    public Result<List<DamConfig>> getConfigTree(@PathVariable String configType) {
        return Result.success(configService.getConfigTree(configType));
    }

    @GetMapping("/navigation")
    public Result<List<DamConfig>> getNavigation() {
        return Result.success(configService.getConfigTree("navigation"));
    }

    @PostMapping("/config")
    public Result<DamConfig> createConfig(@RequestBody DamConfig config) {
        return Result.success(configService.createConfig(config));
    }

    @PutMapping("/config")
    public Result<DamConfig> updateConfig(@RequestBody DamConfig config) {
        return Result.success(configService.updateConfig(config));
    }

    @DeleteMapping("/config/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return Result.success();
    }

    // ==================== 扫描作业接口（dam_scan_job） ====================

    /**
     * 查询扫描作业列表
     */
    @GetMapping("/scan-jobs")
    public Result<List<DamScanJob>> getScanJobs(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String status) {
        return Result.success(technicalMetadataService.getScanJobs(dataSourceId, status));
    }

    /**
     * 扫描作业详情（含快照解析）
     */
    @GetMapping("/scan-jobs/{id}")
    public Result<Map<String, Object>> getScanJobDetail(@PathVariable Long id) {
        return Result.success(technicalMetadataService.getScanJobDetail(id));
    }

    // ==================== 通用字典接口（sys_dict_type / sys_dict_data） ====================

    /**
     * 按字典类型编码查询启用字典项列表（供前端下拉框渲染）
     *
     * @param typeCode 字典类型编码，如 asset_type、ticket_status、governance_status
     * @return 启用中的字典数据列表，按排序号升序
     */
    @GetMapping("/dict/{typeCode}")
    public Result<List<SysDictData>> getDictData(@PathVariable String typeCode) {
        return Result.success(dictService.getDictDataByTypeCode(typeCode));
    }

    /**
     * 查询全部字典类型列表（含禁用，供字典管理界面使用）
     *
     * @return 全部字典类型列表
     */
    @GetMapping("/dict-types")
    public Result<List<SysDictType>> getDictTypes() {
        return Result.success(dictService.getDictTypes());
    }

    /**
     * 分页查询字典类型列表（含禁用，供字典管理界面分页展示）
     */
    @GetMapping("/dict-types/page")
    public Result<PageResult<SysDictType>> getDictTypesPage(@RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(dictService.getDictTypesPage(keyword, page, pageSize));
    }

    /**
     * 查询指定类型下全部字典数据（含禁用，供字典管理界面使用）
     */
    @GetMapping("/dict/{typeCode}/all")
    public Result<List<SysDictData>> getAllDictData(@PathVariable String typeCode) {
        return Result.success(dictService.getAllDictDataByTypeCode(typeCode));
    }

    /**
     * 分页查询指定类型下的字典数据（含禁用，供字典管理抽屉分页展示）
     */
    @GetMapping("/dict/{typeCode}/page")
    public Result<PageResult<SysDictData>> getDictDataPage(@PathVariable String typeCode,
                                                            @RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(dictService.getDictDataPage(typeCode, page, pageSize));
    }

    /**
     * 新增字典类型（编码唯一，自动补全审计字段）
     */
    @PostMapping("/dict/type")
    public Result<SysDictType> createDictType(@RequestBody SysDictType type) {
        return Result.success(dictService.createDictType(type));
    }

    /**
     * 新增字典数据项（自动补全审计字段与排序号）
     */
    @PostMapping("/dict/data")
    public Result<SysDictData> createDictData(@RequestBody SysDictData data) {
        return Result.success(dictService.createDictData(data));
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/dict/type")
    public Result<SysDictType> updateDictType(@RequestBody SysDictType type) {
        return Result.success(dictService.updateDictType(type));
    }

    /**
     * 删除字典类型（级联删除其下全部字典数据）
     */
    @DeleteMapping("/dict/type/{id}")
    public Result<Void> deleteDictType(@PathVariable Long id) {
        dictService.deleteDictType(id);
        return Result.success();
    }

    /**
     * 更新字典数据项
     */
    @PutMapping("/dict/data")
    public Result<SysDictData> updateDictData(@RequestBody SysDictData data) {
        return Result.success(dictService.updateDictData(data));
    }

    /**
     * 删除字典数据项（逻辑删除）
     */
    @DeleteMapping("/dict/data/{id}")
    public Result<Void> deleteDictData(@PathVariable Long id) {
        dictService.deleteDictData(id);
        return Result.success();
    }
}
