package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.catalog.entity.*;
import com.dam.catalog.repository.*;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表体系服务（报表即资产）
 * <p>
 * 报表通过 asset_info(source_metadata_type='REPORT') + report_catalog 扩展表存储，
 * 主题域权限复用 asset_category_rel 与「角色可见主题域」机制。
 * 支持维度树、卡片分页、详情、维护与在线执行（SQL / 存储过程）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportCatalogService {

    /** “未分类”编码 */
    private static final String UNCATEGORIZED = "UNCATEGORIZED";
    /** 单次执行最大返回行数 */
    private static final int MAX_ROWS = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReportCatalogMapper reportMapper;
    private final DamAssetMapper assetMapper;
    private final DamCategoryMapper categoryMapper;
    private final AssetCategoryRelMapper relMapper;
    private final CatalogService catalogService;
    private final DictService dictService;
    private final ReportService reportService;

    // ==================== 维度 ====================

    /**
     * 报表维度树（维度 + 维度值），按当前角色可见主题域过滤
     */
    public List<Map<String, Object>> listDimensions() {
        List<Map<String, Object>> dims = reportMapper.selectDimensions();
        Set<Long> visible = catalogService.currentVisibleDomainIds();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : dims) {
            String dimCode = str(d.get("dimCode"));
            String sourceType = str(d.get("sourceType"));
            String sourceRef = str(d.get("sourceRef"));
            List<Map<String, Object>> values = new ArrayList<>();
            if ("CATEGORY".equalsIgnoreCase(sourceType)) {
                List<DamCategory> domains = categoryMapper.selectList(new LambdaQueryWrapper<DamCategory>()
                        .isNull(DamCategory::getParentId)
                        .eq(DamCategory::getStatus, 1)
                        .ne(DamCategory::getCategoryCode, UNCATEGORIZED)
                        .orderByAsc(DamCategory::getSortOrder));
                for (DamCategory c : domains) {
                    if (visible != null && !visible.contains(c.getId())) continue;
                    values.add(item(c.getId(), c.getCategoryName()));
                }
            } else if ("DICT".equalsIgnoreCase(sourceType) && StrUtil.isNotBlank(sourceRef)) {
                for (SysDictData dd : dictService.getDictDataByTypeCode(sourceRef)) {
                    values.add(item(dd.getDictValue(), dd.getDictLabel()));
                }
            } else {
                Object idRaw = d.get("id");
                if (idRaw instanceof Number n) {
                    for (Map<String, Object> it : reportMapper.selectDimensionItems(n.longValue())) {
                        values.add(item(it.get("itemCode"), it.get("itemName")));
                    }
                }
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("code", dimCode);
            node.put("name", d.get("dimName"));
            node.put("sourceType", sourceType);
            node.put("values", values);
            result.add(node);
        }
        return result;
    }

    private Map<String, Object> item(Object code, Object name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    // ==================== 卡片列表 ====================

    /**
     * 报表卡片分页
     *
     * @param dimCode  维度编码（SUBJECT_DOMAIN / REPORT_TYPE / UPDATE_FREQ / 其它）
     * @param dimValue 维度值
     * @param keyword  名称搜索
     */
    public Map<String, Object> pageCards(String dimCode, String dimValue, String keyword, int page, int pageSize) {
        Set<Long> visible = catalogService.currentVisibleDomainIds();
        if (visible != null && visible.isEmpty()) return emptyPage(page, pageSize);

        LambdaQueryWrapper<ReportCatalog> w = new LambdaQueryWrapper<>();
        w.eq(ReportCatalog::getStatus, 1);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(ReportCatalog::getReportName, keyword)
                    .or().like(ReportCatalog::getReportCode, keyword)
                    .or().like(ReportCatalog::getDescription, keyword));
        }
        // 角色可见主题域收敛
        if (visible != null) {
            String ids = visible.stream().map(String::valueOf).collect(Collectors.joining(","));
            w.apply("EXISTS (SELECT 1 FROM dam_asset.asset_category_rel r WHERE r.asset_id = report_catalog.asset_id "
                    + "AND r.deleted_at IS NULL AND r.subject_domain_id IN (" + ids + "))");
        }
        // 维度过滤
        if (StrUtil.isNotBlank(dimCode) && StrUtil.isNotBlank(dimValue)) {
            switch (dimCode) {
                case "SUBJECT_DOMAIN" -> w.apply("EXISTS (SELECT 1 FROM dam_asset.asset_category_rel r2 "
                                + "WHERE r2.asset_id = report_catalog.asset_id AND r2.deleted_at IS NULL "
                                + "AND r2.subject_domain_id = {0})", parseLong(dimValue));
                case "REPORT_TYPE" -> w.eq(ReportCatalog::getReportTypeId, dimValue);
                case "UPDATE_FREQ" -> w.eq(ReportCatalog::getUpdateFrequency, dimValue);
                default -> w.apply("report_catalog.dimension_codes @> to_jsonb({0}::text)", dimValue);
            }
        }
        w.orderByDesc(ReportCatalog::getUpdatedAt);

        Page<ReportCatalog> p = reportMapper.selectPage(new Page<>(page, pageSize), w);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", toCards(p.getRecords()));
        out.put("total", p.getTotal());
        out.put("page", page);
        out.put("pageSize", pageSize);
        return out;
    }

    private Map<String, Object> emptyPage(int page, int pageSize) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", Collections.emptyList());
        out.put("total", 0);
        out.put("page", page);
        out.put("pageSize", pageSize);
        return out;
    }

    /** 组装卡片展示数据（批量查资产与主题域归属，避免 N+1） */
    private List<Map<String, Object>> toCards(List<ReportCatalog> list) {
        if (list.isEmpty()) return Collections.emptyList();
        List<Long> assetIds = list.stream().map(ReportCatalog::getAssetId).filter(Objects::nonNull).toList();
        Map<Long, DamAsset> assetMap = assetIds.isEmpty() ? Collections.emptyMap()
                : assetMapper.selectByIds(assetIds).stream()
                        .collect(Collectors.toMap(DamAsset::getId, a -> a, (x, y) -> x));
        Map<Long, List<AssetCategoryRel>> relMap = assetIds.isEmpty() ? Collections.emptyMap()
                : relMapper.selectList(new LambdaQueryWrapper<AssetCategoryRel>()
                        .in(AssetCategoryRel::getAssetId, assetIds))
                        .stream().collect(Collectors.groupingBy(AssetCategoryRel::getAssetId));
        Set<Long> catIds = new HashSet<>();
        relMap.values().forEach(rels -> rels.forEach(r -> catIds.add(r.getCategoryId())));
        Map<Long, DamCategory> catMap = catIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectByIds(catIds).stream()
                        .collect(Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));
        Set<Long> domainIds = catMap.values().stream().map(DamCategory::getParentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DamCategory> domainMap = domainIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectByIds(domainIds).stream()
                        .collect(Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));

        List<Map<String, Object>> cards = new ArrayList<>();
        for (ReportCatalog rc : list) {
            DamAsset asset = assetMap.get(rc.getAssetId());
            List<AssetCategoryRel> rels = relMap.getOrDefault(rc.getAssetId(), Collections.emptyList());
            List<String> paths = new ArrayList<>();
            Long domainId = null;
            for (AssetCategoryRel r : rels) {
                DamCategory c = catMap.get(r.getCategoryId());
                if (c == null) continue;
                DamCategory d = c.getParentId() != null ? domainMap.get(c.getParentId()) : null;
                paths.add((d != null ? d.getCategoryName() + "/" : "") + c.getCategoryName());
                if (domainId == null) domainId = r.getSubjectDomainId();
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", rc.getId());
            card.put("assetId", rc.getAssetId());
            card.put("reportCode", rc.getReportCode());
            card.put("reportName", rc.getReportName());
            card.put("reportTypeId", rc.getReportTypeId());
            card.put("execType", rc.getExecType());
            card.put("updateFrequency", rc.getUpdateFrequency());
            card.put("owner", rc.getOwner());
            card.put("description", StrUtil.isNotBlank(rc.getDescription()) ? rc.getDescription()
                    : (asset != null ? asset.getDescription() : null));
            card.put("coverImage", rc.getCoverImage());
            card.put("dimensionCodes", parseJsonArray(rc.getDimensionCodes()));
            card.put("subjectDomainId", domainId);
            card.put("categoryPathText", String.join("；", paths));
            card.put("updatedAt", rc.getUpdatedAt());
            cards.add(card);
        }
        return cards;
    }

    // ==================== 详情 / 维护 ====================

    /** 报表详情（含资产信息、主题域归属、参数定义） */
    public Map<String, Object> getDetail(Long id) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        DamAsset asset = rc.getAssetId() != null ? assetMapper.selectById(rc.getAssetId()) : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", rc);
        out.put("asset", asset);
        out.put("subjectDomains", catalogService.listAssetCategories(rc.getAssetId()));
        out.put("paramDefs", parseJsonArray(rc.getParamDefs()));
        out.put("dimensionCodes", parseJsonArray(rc.getDimensionCodes()));
        return out;
    }

    /**
     * 新建/更新报表（同时维护报表资产与主题域归属）
     *
     * @param body 报表属性 + 可选 subjectDomainId/categoryId
     */
    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        Long id = parseLong(body.get("id"));
        String reportCode = str(body.get("reportCode"));
        String reportName = str(body.get("reportName"));
        if (StrUtil.isBlank(reportName)) throw new BusinessException("请填写报表名称");
        if (StrUtil.isBlank(reportCode)) {
            reportCode = "RPT_" + System.currentTimeMillis();
        }
        Long dataSourceId = parseLong(body.get("dataSourceId"));
        Long categoryId = parseLong(body.get("categoryId"));
        Long subjectDomainId = parseLong(body.get("subjectDomainId"));
        String execType = StrUtil.isNotBlank(str(body.get("execType"))) ? str(body.get("execType")) : "SQL";
        if (!List.of("SQL", "PROC", "HTTP", "IFRAME", "STATIC").contains(execType)) {
            throw new BusinessException("不支持的执行方式: " + execType);
        }

        ReportCatalog rc = id != null ? requireReport(id) : new ReportCatalog();
        if (id == null) {
            // 编码唯一
            Long dup = reportMapper.selectCount(new LambdaQueryWrapper<ReportCatalog>()
                    .eq(ReportCatalog::getReportCode, reportCode));
            if (dup != null && dup > 0) throw new BusinessException("报表编码已存在: " + reportCode);
            rc.setReportCode(reportCode);
            rc.setCreatedAt(LocalDateTime.now());
            rc.setCreatedBy(currentUser());
        }
        rc.setReportName(reportName);
        rc.setReportTypeId(str(body.get("reportTypeId")));
        rc.setExecType(execType);
        rc.setDataSourceId(dataSourceId);
        rc.setSqlContent(str(body.get("sqlContent")));
        rc.setEndpointUrl(str(body.get("endpointUrl")));
        rc.setCoverImage(str(body.get("coverImage")));
        rc.setUpdateFrequency(str(body.get("updateFrequency")));
        rc.setOwner(str(body.get("owner")));
        rc.setDescription(str(body.get("description")));
        rc.setSortOrder(body.get("sortOrder") instanceof Number n ? n.longValue() : 0L);
        rc.setStatus(body.get("status") instanceof Number n ? n.intValue() : 1);
        rc.setDimensionCodes(toJson(body.get("dimensionCodes")));
        rc.setParamDefs(toJson(body.get("paramDefs")));
        rc.setUpdatedAt(LocalDateTime.now());
        rc.setUpdatedBy(currentUser());

        // 1. 资产（报表即资产）
        Long assetId = rc.getAssetId();
        DamAsset asset = assetId != null ? assetMapper.selectById(assetId) : null;
        boolean newAsset = asset == null;
        if (newAsset) {
            asset = new DamAsset();
            asset.setCreatedAt(LocalDateTime.now());
            asset.setCreatedBy(currentUser());
        }
        asset.setAssetName(reportName);
        asset.setAssetCode(newAsset ? StrUtil.blankToDefault(reportCode, "RPT") : asset.getAssetCode());
        asset.setSourceMetadataType("REPORT");
        asset.setAssetTypeId(resolveReportAssetTypeId());
        asset.setDescription(StrUtil.isNotBlank(rc.getDescription()) ? rc.getDescription() : reportName);
        asset.setDataSourceId(dataSourceId);
        asset.setStatus(1);
        asset.setUpdatedAt(LocalDateTime.now());
        asset.setUpdatedBy(currentUser());
        if (categoryId != null) {
            asset.setCategoryId(categoryId);
        } else if (newAsset) {
            asset.setCategoryId(resolveUncategorizedId());
        }
        if (newAsset) {
            assetMapper.insert(asset);
        } else {
            assetMapper.updateById(asset);
        }
        rc.setAssetId(asset.getId());
        if (id == null) {
            reportMapper.insert(rc);
        } else {
            reportMapper.updateById(rc);
        }

        // 2. 主题域归属（传了类目才写；同域唯一由服务保证）
        if (categoryId != null) {
            catalogService.saveAssetCategory(asset.getId(), subjectDomainId, categoryId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rc.getId());
        out.put("assetId", asset.getId());
        out.put("reportCode", rc.getReportCode());
        return out;
    }

    /** 逻辑删除报表及其资产 */
    @Transactional
    public void delete(Long id) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        LocalDateTime now = LocalDateTime.now();
        rc.setDeletedAt(now);
        rc.setUpdatedAt(now);
        rc.setUpdatedBy(currentUser());
        reportMapper.updateById(rc);
        DamAsset asset = rc.getAssetId() != null ? assetMapper.selectById(rc.getAssetId()) : null;
        if (asset != null) {
            asset.setDeletedAt(now);
            asset.setUpdatedAt(now);
            assetMapper.updateById(asset);
        }
    }

    // ==================== 在线执行 ====================

    /**
     * 在线执行报表（SQL / 存储过程），返回结果集
     *
     * @param params 参数名 → 值（对应 sqlContent 中的 :param）
     */
    public Map<String, Object> run(Long id, Map<String, Object> params) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        String execType = StrUtil.blankToDefault(rc.getExecType(), "SQL");
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        switch (execType) {
            case "SQL", "PROC" -> {
                Map<String, Object> data = reportService.executeWithParams(
                        rc.getDataSourceId(), rc.getSqlContent(), params, MAX_ROWS, "PROC".equals(execType));
                result.putAll(data);
                result.put("execType", execType);
            }
            case "IFRAME", "HTTP" -> {
                if (StrUtil.isBlank(rc.getEndpointUrl())) throw new BusinessException("报表未配置访问地址");
                result.put("execType", execType);
                result.put("endpointUrl", rc.getEndpointUrl());
                result.put("rowCount", 0);
            }
            default -> {
                result.put("execType", "STATIC");
                result.put("rowCount", 0);
                result.put("message", rc.getDescription());
            }
        }
        result.put("reportId", rc.getId());
        result.put("reportName", rc.getReportName());
        result.put("elapsedMs", System.currentTimeMillis() - start);
        log.info("报表执行: id={}, code={}, execType={}, rows={}, elapsed={}ms, user={}",
                rc.getId(), rc.getReportCode(), execType, result.get("rowCount"),
                result.get("elapsedMs"), currentUser());
        return result;
    }

    // ==================== 内部工具 ====================

    private ReportCatalog requireReport(Long id) {
        ReportCatalog rc = id != null ? reportMapper.selectById(id) : null;
        if (rc == null || rc.getDeletedAt() != null) throw new BusinessException("报表不存在");
        return rc;
    }

    /** 报表可见性校验：未归属任何可见主题域则拒绝 */
    private void assertPermitted(Long assetId) {
        Set<Long> visible = catalogService.currentVisibleDomainIds();
        if (visible == null) return;
        List<AssetCategoryRel> rels = relMapper.selectList(
                new LambdaQueryWrapper<AssetCategoryRel>().eq(AssetCategoryRel::getAssetId, assetId));
        boolean allowed = rels.stream().anyMatch(r -> visible.contains(r.getSubjectDomainId()));
        if (!allowed) throw new BusinessException("无权查看该报表（不在当前角色可见主题域内）");
    }

    /** 报表类资产的 asset_type 字典项ID */
    private Long resolveReportAssetTypeId() {
        for (SysDictData d : dictService.getDictDataByTypeCode("asset_type")) {
            if ("report".equalsIgnoreCase(d.getDictValue())) return d.getId();
        }
        return null;
    }

    /** 未分类兜底ID */
    private Long resolveUncategorizedId() {
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

    private Long parseLong(Object v) {
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

    private String toJson(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return StrUtil.isBlank(s) ? null : s;
        try {
            return MAPPER.writeValueAsString(v);
        } catch (Exception e) {
            throw new BusinessException("JSON 字段格式错误: " + e.getMessage());
        }
    }

    private Object parseJsonArray(String json) {
        if (StrUtil.isBlank(json)) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
