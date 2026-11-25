package com.dam.asset.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.asset.catalog.entity.AssetCategoryRel;
import com.dam.asset.catalog.entity.DamAsset;
import com.dam.asset.catalog.entity.DamCategory;
import com.dam.asset.catalog.repository.AssetCategoryRelMapper;
import com.dam.asset.catalog.repository.DamAssetMapper;
import com.dam.asset.catalog.repository.DamCategoryMapper;
import com.dam.asset.metadata.entity.MetadataColumn;
import com.dam.asset.metadata.entity.MetadataSchema;
import com.dam.asset.metadata.entity.MetadataTable;
import com.dam.asset.metadata.repository.MetadataColumnMapper;
import com.dam.asset.metadata.repository.MetadataSchemaMapper;
import com.dam.asset.metadata.repository.MetadataTableMapper;
import com.dam.asset.metric.entity.MetricDefinition;
import com.dam.asset.shared.port.KnowledgeSearchPort;
import com.dam.asset.shared.port.MetricLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 报表模块专用的 DAM 数据服务。
 * <p>
 * analytics-report 已物理脱离 asset-catalog 的 Maven 依赖，报表所需的资产、分类、
 * 主题域、元数据表结构、指标与知识检索能力统一由本服务以 HTTP 接口形式暴露，
 * 报表模块不再直接访问 dam_dam / dam_dam 表。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDataService {

    /** “未分类”分类编码 */
    private static final String UNCATEGORIZED = "UNCATEGORIZED";

    private final DamAssetMapper assetMapper;
    private final DamCategoryMapper categoryMapper;
    private final AssetCategoryRelMapper assetCategoryRelMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final MetadataColumnMapper metadataColumnMapper;
    private final MetadataSchemaMapper metadataSchemaMapper;
    private final CatalogService catalogService;
    private final MetricLookupPort metricLookupPort;
    private final KnowledgeSearchPort knowledgeSearchPort;

    // ==================== 主题域 / 分类 ====================

    /**
     * 当前角色可见主题域。
     *
     * @return {restricted:true, ids:[...]}；restricted=false 表示不受限（admin / 角色未配置）
     */
    public Map<String, Object> visibleDomains() {
        Set<Long> ids = catalogService.currentVisibleDomainIds();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restricted", ids != null);
        result.put("ids", ids == null ? List.of() : ids);
        return result;
    }

    /** 全部启用的一级主题域（排除“未分类”），按排序号升序 */
    public List<Map<String, Object>> domains() {
        List<DamCategory> list = categoryMapper.selectList(new LambdaQueryWrapper<DamCategory>()
                .isNull(DamCategory::getParentId)
                .eq(DamCategory::getStatus, 1)
                .ne(DamCategory::getCategoryCode, UNCATEGORIZED)
                .orderByAsc(DamCategory::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DamCategory c : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("categoryCode", c.getCategoryCode());
            item.put("categoryName", c.getCategoryName());
            result.add(item);
        }
        return result;
    }

    /** 按分类编码查询分类（报表保存时的“未分类”兜底） */
    public Map<String, Object> categoryByCode(String code) {
        if (StrUtil.isBlank(code)) return null;
        DamCategory c = categoryMapper.selectOne(new LambdaQueryWrapper<DamCategory>()
                .eq(DamCategory::getCategoryCode, code)
                .last("LIMIT 1"));
        if (c == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.getId());
        result.put("categoryCode", c.getCategoryCode());
        result.put("categoryName", c.getCategoryName());
        return result;
    }

    // ==================== 资产 ====================

    public List<DamAsset> assets(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return assetMapper.selectByIds(ids);
    }

    /** 指定数据源下已维护描述的资产（报表生成时用资产描述覆盖表注释） */
    public List<DamAsset> assetsByDataSource(Long dataSourceId) {
        if (dataSourceId == null) return List.of();
        return assetMapper.selectList(new LambdaQueryWrapper<DamAsset>()
                .eq(DamAsset::getDataSourceId, dataSourceId)
                .isNotNull(DamAsset::getDescription));
    }

    public DamAsset asset(Long id) {
        return id == null ? null : assetMapper.selectById(id);
    }

    /**
     * 报表资产新建/更新（报表即资产）。
     * <p>
     * 与报表模块原有落库逻辑保持一致：传 id 且资产存在时为更新；否则新建，
     * 新建时使用 body.assetCode 作为资产编码，未指定分类时归入“未分类”。
     *
     * @param body 资产字段（id/assetName/assetCode/assetTypeId/description/dataSourceId/categoryId/operator）
     * @return 落库后的资产
     */
    @Transactional
    public DamAsset upsertAsset(Map<String, Object> body) {
        Long id = longVal(body.get("id"));
        String operator = StrUtil.blankToDefault(str(body.get("operator")), currentUser());
        DamAsset asset = id != null ? assetMapper.selectById(id) : null;
        boolean isNew = asset == null;
        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            asset = new DamAsset();
            asset.setCreatedAt(now);
            asset.setCreatedBy(operator);
        }
        asset.setAssetName(str(body.get("assetName")));
        asset.setAssetCode(isNew
                ? StrUtil.blankToDefault(str(body.get("assetCode")), "RPT")
                : asset.getAssetCode());
        asset.setSourceMetadataType("REPORT");
        asset.setAssetTypeId(longVal(body.get("assetTypeId")));
        asset.setDescription(str(body.get("description")));
        asset.setDataSourceId(longVal(body.get("dataSourceId")));
        asset.setStatus(body.get("status") instanceof Number n ? n.intValue() : 1);
        Long categoryId = longVal(body.get("categoryId"));
        if (categoryId != null) {
            asset.setCategoryId(categoryId);
        } else if (isNew) {
            asset.setCategoryId(uncategorizedId());
        }
        asset.setUpdatedAt(now);
        asset.setUpdatedBy(operator);
        if (isNew) {
            assetMapper.insert(asset);
        } else {
            assetMapper.updateById(asset);
        }
        return asset;
    }

    /** 报表删除时同步逻辑删除报表资产（与报表软删除口径一致，不物理删除） */
    @Transactional
    public void softDeleteAsset(Long id) {
        DamAsset asset = id == null ? null : assetMapper.selectById(id);
        if (asset == null) return;
        LocalDateTime now = LocalDateTime.now();
        asset.setDeletedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.updateById(asset);
    }

    // ==================== 资产分类归属 ====================

    /** 资产的多主题域归属（报表详情展示） */
    public List<Map<String, Object>> assetCategories(Long assetId) {
        if (assetId == null) return List.of();
        return catalogService.listAssetCategories(assetId);
    }

    /**
     * 指定主题域下的资产ID（未删除的归属关系）。
     * <p>供报表卡片按可见主题域/主题域维度过滤，避免报表模块直接查询 dam_dam.asset_category_rel。</p>
     */
    public List<Long> assetIdsForDomains(List<Long> domainIds) {
        if (domainIds == null || domainIds.isEmpty()) return List.of();
        return assetCategoryRelMapper.selectList(new LambdaQueryWrapper<AssetCategoryRel>()
                        .in(AssetCategoryRel::getSubjectDomainId, domainIds)
                        .isNull(AssetCategoryRel::getDeletedAt))
                .stream().map(AssetCategoryRel::getAssetId)
                .filter(Objects::nonNull).distinct().toList();
    }

    /** 资产直接关联的主题域ID（报表可见性校验，不包含主分类兜底） */
    public List<Long> assetDomainIds(Long assetId) {
        if (assetId == null) return List.of();
        return assetCategoryRelMapper.selectList(new LambdaQueryWrapper<AssetCategoryRel>()
                        .eq(AssetCategoryRel::getAssetId, assetId))
                .stream().map(AssetCategoryRel::getSubjectDomainId).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 批量资产分类路径（报表卡片展示）。
     * <p>按归属关系计算「主题域/类目」文本与主题域ID，保持与报表卡片原有逻辑一致。</p>
     */
    public Map<Long, Map<String, Object>> assetCategoryPaths(List<Long> assetIds) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;
        List<AssetCategoryRel> rels = assetCategoryRelMapper.selectList(
                new LambdaQueryWrapper<AssetCategoryRel>().in(AssetCategoryRel::getAssetId, assetIds));
        Map<Long, List<AssetCategoryRel>> relMap = new HashMap<>();
        for (AssetCategoryRel rel : rels) {
            relMap.computeIfAbsent(rel.getAssetId(), k -> new ArrayList<>()).add(rel);
        }
        Set<Long> catIds = new HashSet<>();
        for (AssetCategoryRel rel : rels) {
            if (rel.getCategoryId() != null) catIds.add(rel.getCategoryId());
        }
        Map<Long, DamCategory> catMap = catIds.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(catIds).stream()
                        .collect(java.util.stream.Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));
        Set<Long> domainIds = new HashSet<>();
        for (DamCategory c : catMap.values()) {
            if (c.getParentId() != null) domainIds.add(c.getParentId());
        }
        Map<Long, DamCategory> domainMap = domainIds.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(domainIds).stream()
                        .collect(java.util.stream.Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));

        for (Long assetId : assetIds) {
            List<String> paths = new ArrayList<>();
            Long domainId = null;
            for (AssetCategoryRel rel : relMap.getOrDefault(assetId, List.of())) {
                DamCategory c = catMap.get(rel.getCategoryId());
                if (c == null) continue;
                DamCategory d = c.getParentId() != null ? domainMap.get(c.getParentId()) : null;
                paths.add((d != null ? d.getCategoryName() + "/" : "") + c.getCategoryName());
                if (domainId == null) domainId = rel.getSubjectDomainId();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("paths", paths);
            item.put("subjectDomainId", domainId);
            result.put(assetId, item);
        }
        return result;
    }

    /** 保存资产在某个主题域下的类目归属 */
    @Transactional
    public void saveAssetCategory(Long assetId, Long subjectDomainId, Long categoryId) {
        catalogService.saveAssetCategory(assetId, subjectDomainId, categoryId);
    }

    // ==================== 技术元数据（报表 SQL 生成上下文） ====================

    /** 指定数据源下已同步的表结构与字段（含 schema 名） */
    public List<Map<String, Object>> tables(Long dataSourceId) {
        if (dataSourceId == null) return List.of();
        List<MetadataTable> tables = metadataTableMapper.selectList(new LambdaQueryWrapper<MetadataTable>()
                .eq(MetadataTable::getDatasourceId, dataSourceId)
                .orderByAsc(MetadataTable::getTableName));
        if (tables.isEmpty()) return List.of();

        Set<Long> schemaIds = new HashSet<>();
        for (MetadataTable t : tables) {
            if (t.getSchemaId() != null) schemaIds.add(t.getSchemaId());
        }
        Map<Long, String> schemaNames = new HashMap<>();
        if (!schemaIds.isEmpty()) {
            for (MetadataSchema s : metadataSchemaMapper.selectByIds(schemaIds)) {
                if (s != null) schemaNames.put(s.getId(), s.getSchemaName());
            }
        }

        Set<Long> tableIds = new HashSet<>();
        for (MetadataTable t : tables) tableIds.add(t.getId());
        Map<Long, List<MetadataColumn>> columnsMap = new HashMap<>();
        if (!tableIds.isEmpty()) {
            for (MetadataColumn c : metadataColumnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                    .in(MetadataColumn::getTableId, tableIds)
                    .orderByAsc(MetadataColumn::getOrdinalPosition))) {
                if (c.getTableId() != null) {
                    columnsMap.computeIfAbsent(c.getTableId(), k -> new ArrayList<>()).add(c);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MetadataTable t : tables) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("dataSourceId", t.getDatasourceId());
            m.put("schemaName", t.getSchemaId() == null ? null : schemaNames.get(t.getSchemaId()));
            m.put("tableName", t.getTableName());
            m.put("tableComment", t.getTableComment());
            m.put("subjectDomain", t.getSubjectDomain());
            List<Map<String, Object>> columns = new ArrayList<>();
            for (MetadataColumn c : columnsMap.getOrDefault(t.getId(), List.of())) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId());
                cm.put("columnName", c.getColumnName());
                cm.put("columnType", c.getColumnType());
                cm.put("columnComment", c.getColumnComment());
                cm.put("isPrimaryKey", c.getIsPrimaryKey());
                cm.put("isForeignKey", c.getIsForeignKey());
                cm.put("referenceTableName", c.getReferenceTableName());
                cm.put("ordinalPosition", c.getOrdinalPosition());
                columns.add(cm);
            }
            m.put("columns", columns);
            result.add(m);
        }
        return result;
    }

    // ==================== 指标 / 知识库 ====================

    /** 指定数据源下启用指标（报表 Prompt 口径参考） */
    public List<MetricDefinition> metrics(Long dataSourceId) {
        if (dataSourceId == null) return List.of();
        return metricLookupPort.listActiveByDataSource(dataSourceId);
    }

    /** 检索业务知识库（报表 Prompt 口径参考） */
    public Map<String, Object> searchKnowledge(String query, int limit) {
        return knowledgeSearchPort.search(query, limit);
    }

    // ==================== 内部工具 ====================

    /** 未分类兜底ID（与报表模块原有逻辑一致：不存在时返回 null） */
    private Long uncategorizedId() {
        DamCategory c = categoryMapper.selectOne(new LambdaQueryWrapper<DamCategory>()
                .eq(DamCategory::getCategoryCode, UNCATEGORIZED).last("LIMIT 1"));
        return c != null ? c.getId() : null;
    }

    private String currentUser() {
        try {
            Object id = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            return id != null ? String.valueOf(id) : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private Long longVal(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v != null && StrUtil.isNotBlank(String.valueOf(v))) {
            try {
                return Long.valueOf(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }
}
