package com.dam.analytics.report.service;



import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.analytics.report.client.DictReadClient;
import com.dam.analytics.report.client.ReportDataClient;
import com.dam.analytics.report.entity.ReportCatalog;
import com.dam.analytics.report.repository.ReportCatalogMapper;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.security.DataScopeContext;
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

    /** 单次执行最大返回行数 */
    private static final int MAX_ROWS = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReportCatalogMapper reportMapper;
    private final ReportDataClient reportDataClient;
    private final DictReadClient dictReadClient;
    private final ReportService reportService;

    // ==================== 维度 ====================

    /**
     * 报表维度树（维度 + 维度值），按当前角色可见主题域过滤
     */
    public List<Map<String, Object>> listDimensions() {
        List<Map<String, Object>> dims = reportMapper.selectDimensions();
        Set<Long> visible = reportDataClient.visibleDomainIds();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : dims) {
            String dimCode = str(d.get("dimCode"));
            String sourceType = str(d.get("sourceType"));
            String sourceRef = str(d.get("sourceRef"));
            List<Map<String, Object>> values = new ArrayList<>();
            if ("CATEGORY".equalsIgnoreCase(sourceType)) {
                for (Map<String, Object> c : reportDataClient.domains()) {
                    Long domainId = parseLong(c.get("id"));
                    if (visible != null && !visible.contains(domainId)) continue;
                    values.add(item(domainId, c.get("categoryName")));
                }
            } else if ("DICT".equalsIgnoreCase(sourceType) && StrUtil.isNotBlank(sourceRef)) {
                for (Map<String, Object> dd : dictReadClient.byType(sourceRef)) {
                    values.add(item(text(dd.get("dictValue")), text(dd.get("dictLabel"))));
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
        Set<Long> visible = reportDataClient.visibleDomainIds();
        if (visible != null && visible.isEmpty()) return emptyPage(page, pageSize);

        LambdaQueryWrapper<ReportCatalog> w = new LambdaQueryWrapper<>();
        w.eq(ReportCatalog::getStatus, 1);
        // 删除是软删除：只改 deleted_at，卡片列表必须显式排除已删除报表
        w.isNull(ReportCatalog::getDeletedAt);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(ReportCatalog::getReportName, keyword)
                    .or().like(ReportCatalog::getReportCode, keyword)
                    .or().like(ReportCatalog::getDescription, keyword));
        }
        // token 数据范围收敛
        if (DataScopeContext.isDepartment()) {
            String dept = DataScopeContext.department();
            if (dept.isBlank()) w.eq(ReportCatalog::getId, -1L);
            else w.and(q -> q.eq(ReportCatalog::getDepartment, dept).or().isNull(ReportCatalog::getDepartment));
        } else if (DataScopeContext.isSelf()) {
            String username = DataScopeContext.username();
            if (username.isBlank()) w.eq(ReportCatalog::getId, -1L);
            else w.eq(ReportCatalog::getCreatedBy, username);
        }
        // 角色可见主题域收敛：经 DAM 接口取主题域下的资产ID，报表库不直连 dam_dam 表
        if (visible != null) {
            List<Long> domainAssetIds = reportDataClient.assetIdsForDomains(visible);
            if (domainAssetIds.isEmpty()) return emptyPage(page, pageSize);
            w.in(ReportCatalog::getAssetId, domainAssetIds);
        }
        // 维度过滤
        if (StrUtil.isNotBlank(dimCode) && StrUtil.isNotBlank(dimValue)) {
            switch (dimCode) {
                case "SUBJECT_DOMAIN" -> {
                    Long subjectDomainId = parseLong(dimValue);
                    List<Long> domainAssetIds = subjectDomainId == null ? List.of()
                            : reportDataClient.assetIdsForDomains(List.of(subjectDomainId));
                    if (domainAssetIds.isEmpty()) w.eq(ReportCatalog::getId, -1L);
                    else w.in(ReportCatalog::getAssetId, domainAssetIds);
                }
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

    /** 组装卡片展示数据（批量经 DAM 接口查资产与主题域归属，避免 N+1） */
    private List<Map<String, Object>> toCards(List<ReportCatalog> list) {
        if (list.isEmpty()) return Collections.emptyList();
        List<Long> assetIds = list.stream().map(ReportCatalog::getAssetId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Map<String, Object>> assetMap = new HashMap<>();
        for (Map<String, Object> asset : reportDataClient.assets(assetIds)) {
            Long id = parseLong(asset.get("id"));
            if (id != null) assetMap.put(id, asset);
        }
        Map<Long, Map<String, Object>> categoryPaths = reportDataClient.assetCategoryPaths(assetIds);

        List<Map<String, Object>> cards = new ArrayList<>();
        for (ReportCatalog rc : list) {
            Map<String, Object> asset = assetMap.get(rc.getAssetId());
            Map<String, Object> categoryInfo = categoryPaths.getOrDefault(rc.getAssetId(), Collections.emptyMap());
            List<String> paths = new ArrayList<>();
            if (categoryInfo.get("paths") instanceof List<?> pathList) {
                for (Object path : pathList) paths.add(String.valueOf(path));
            }
            Long domainId = parseLong(categoryInfo.get("subjectDomainId"));
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
                    : (asset != null && asset.get("description") != null
                        ? String.valueOf(asset.get("description")) : null));
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

    /** 供异步任务使用：按ID取报表，已删除的拒绝 */
    public ReportCatalog getReport(Long id) {
        return requireReport(id);
    }

    /** 报表详情（含资产信息、主题域归属、参数定义） */
    public Map<String, Object> getDetail(Long id) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        Map<String, Object> asset = reportDataClient.asset(rc.getAssetId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", rc);
        out.put("asset", asset);
        out.put("subjectDomains", reportDataClient.assetCategories(rc.getAssetId()));
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
        return doSave(body, currentUser());
    }

    /**
     * 异步 AI 任务生成报表时使用：显式传入操作人，避免异步线程没有 Sa-Token 上下文。
     */
    @Transactional
    public Map<String, Object> saveGenerated(Map<String, Object> body, String operator) {
        return doSave(body, operator == null || operator.isBlank() ? "system" : operator);
    }

    private Map<String, Object> doSave(Map<String, Object> body, String operator) {
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
            rc.setCreatedBy(operator);
            if (StrUtil.isBlank(rc.getDepartment())) rc.setDepartment(DataScopeContext.department());
        }
        rc.setReportName(reportName);
        rc.setReportTypeId(str(body.get("reportTypeId")));
        rc.setExecType(execType);
        rc.setDataSourceId(dataSourceId);
        rc.setSqlContent(str(body.get("sqlContent")));
        rc.setDrillSql(str(body.get("drillSql")));
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
        rc.setUpdatedBy(operator);

        // 1. 资产（报表即资产，经 DAM 接口落库）
        Map<String, Object> assetBody = new LinkedHashMap<>();
        assetBody.put("id", rc.getAssetId());
        assetBody.put("assetName", reportName);
        assetBody.put("assetCode", StrUtil.blankToDefault(reportCode, "RPT"));
        assetBody.put("sourceMetadataType", "REPORT");
        assetBody.put("assetTypeId", resolveReportAssetTypeId());
        assetBody.put("description", StrUtil.isNotBlank(rc.getDescription()) ? rc.getDescription() : reportName);
        assetBody.put("dataSourceId", dataSourceId);
        assetBody.put("status", 1);
        assetBody.put("categoryId", categoryId);
        assetBody.put("operator", operator);
        Map<String, Object> asset = reportDataClient.upsertAsset(assetBody);
        Long newAssetId = parseLong(asset.get("id"));
        rc.setAssetId(newAssetId);
        if (id == null) {
            reportMapper.insert(rc);
        } else {
            reportMapper.updateById(rc);
        }

        // 2. 主题域归属（传了类目才写；同域唯一由服务保证）
        if (categoryId != null) {
            reportDataClient.saveAssetCategory(newAssetId, subjectDomainId, categoryId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rc.getId());
        out.put("assetId", newAssetId);
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
        reportDataClient.deleteAsset(rc.getAssetId());
    }

    // ==================== 在线执行 ====================

    /**
     * 在线执行报表（SQL / 存储过程），返回结果集
     *
     * @param params 参数名 → 值（对应 sqlContent 中的 :param）
     */
    public Map<String, Object> run(Long id, Map<String, Object> params) {
        return run(id, params, null);
    }

    /**
     * 执行报表，可临时指定预览数据源；不传则使用报表配置的数据源。
     */
    public Map<String, Object> run(Long id, Map<String, Object> params, Long previewDataSourceId) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        String execType = StrUtil.blankToDefault(rc.getExecType(), "SQL");
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        switch (execType) {
            case "SQL", "PROC" -> {
                Long execDataSourceId = previewDataSourceId != null ? previewDataSourceId : rc.getDataSourceId();
                Map<String, Object> data = reportService.executeWithParams(
                        execDataSourceId, rc.getSqlContent(), params, MAX_ROWS, "PROC".equals(execType));
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

    /**
     * 执行下钻明细 SQL。
     */
    public Map<String, Object> runDrill(Long id, Map<String, Object> params, Long previewDataSourceId) {
        ReportCatalog rc = requireReport(id);
        assertPermitted(rc.getAssetId());
        if (StrUtil.isBlank(rc.getDrillSql())) throw new BusinessException("该报表未配置下钻 SQL");
        Long execDataSourceId = previewDataSourceId != null ? previewDataSourceId : rc.getDataSourceId();
        Map<String, Object> data = reportService.executeWithParams(
                execDataSourceId, rc.getDrillSql(), params, MAX_ROWS, false);
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.put("reportId", rc.getId());
        result.put("drill", true);
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
        Set<Long> visible = reportDataClient.visibleDomainIds();
        if (visible == null) return;
        Set<Long> domains = reportDataClient.assetDomainIds(assetId);
        boolean allowed = domains.stream().anyMatch(visible::contains);
        if (!allowed) throw new BusinessException("无权查看该报表（不在当前角色可见主题域内）");
    }

    /** 报表类资产的 asset_type 字典项ID */
    private Long resolveReportAssetTypeId() {
        for (Map<String, Object> d : dictReadClient.byType("asset_type")) {
            if ("report".equalsIgnoreCase(text(d.get("dictValue")))) return number(d.get("id"));
        }
        return null;
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

    private String text(Object v) { return v == null ? "" : String.valueOf(v); }
    private Long number(Object v) { return v instanceof Number n ? n.longValue() : null; }
}
