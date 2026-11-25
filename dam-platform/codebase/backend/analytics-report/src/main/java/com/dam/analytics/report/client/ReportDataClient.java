package com.dam.analytics.report.client;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.analytics.report.client.dto.CatalogTable;
import com.dam.platform.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;
import java.util.function.Supplier;

/**
 * DAM（asset-catalog）数据接口客户端。
 * <p>
 * 报表模块已物理脱离 asset-catalog 的 Maven 依赖，数据源、资产、分类/主题域、
 * 技术元数据表结构、指标与知识检索统一通过本客户端调用
 * {@code /api/catalog/report-data/**} 的 HTTP 接口获取，
 * 不再直接依赖 DAM Mapper 或访问 dam_asset / dam_metadata 表。
 *
 * @author DAM Platform
 */
@Component
public class ReportDataClient {

    /** JSON 解析（技术元数据 DTO 反序列化） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;

    public ReportDataClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ==================== 数据源 / 资产注释 ====================

    public Map<String, Object> dataSource(Long id) {
        return mapData(getData("/api/catalog/report-data/data-source/" + id));
    }

    public Map<String, Object> assetComments(Long dataSourceId) {
        Map<String, Object> data = mapData(getData(
                "/api/catalog/report-data/asset-comments?dataSourceId=" + dataSourceId));
        return data == null ? Map.of() : data;
    }

    // ==================== 主题域 / 分类 ====================

    /**
     * 当前角色可见主题域。
     *
     * @return null 表示不受限（admin / 角色未配置）
     */
    public Set<Long> visibleDomainIds() {
        Map<String, Object> data = mapData(getData("/api/catalog/report-data/visible-domains"));
        if (data == null || !Boolean.TRUE.equals(data.get("restricted"))) return null;
        Set<Long> ids = new LinkedHashSet<>();
        if (data.get("ids") instanceof List<?> list) {
            for (Object raw : list) {
                Long id = longVal(raw);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    /** 全部启用的一级主题域 */
    public List<Map<String, Object>> domains() {
        return listOfMaps(getData("/api/catalog/report-data/domains"));
    }

    /** 按编码查询分类ID，不存在返回 null */
    public Long categoryIdByCode(String code) {
        Map<String, Object> data = mapData(getData(
                "/api/catalog/report-data/category-by-code?code=" + code));
        return data == null ? null : longVal(data.get("id"));
    }

    // ==================== 资产 ====================

    public List<Map<String, Object>> assets(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return listOfMaps(getData("/api/catalog/report-data/assets?ids=" + join(ids)));
    }

    public Map<String, Object> asset(Long id) {
        return id == null ? null : mapData(getData("/api/catalog/report-data/assets/" + id));
    }

    /** 报表资产新建/更新（报表即资产） */
    public Map<String, Object> upsertAsset(Map<String, Object> body) {
        Map<String, Object> data = mapData(postData("/api/catalog/report-data/assets/upsert", body));
        if (data == null) throw new BusinessException("DAM 资产保存失败");
        return data;
    }

    /** 报表删除时同步逻辑删除报表资产 */
    public void deleteAsset(Long id) {
        if (id == null) return;
        postData("/api/catalog/report-data/assets/" + id + "/delete", Map.of());
    }

    // ==================== 资产分类归属 ====================

    /** 资产的多主题域归属（报表详情） */
    public List<Map<String, Object>> assetCategories(Long assetId) {
        if (assetId == null) return List.of();
        return listOfMaps(getData("/api/catalog/report-data/asset-categories/" + assetId));
    }

    /** 资产直接关联的主题域ID（报表可见性校验） */
    public Set<Long> assetDomainIds(Long assetId) {
        if (assetId == null) return Set.of();
        Set<Long> ids = new LinkedHashSet<>();
        Object raw = getData("/api/catalog/report-data/asset-domain-ids/" + assetId);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = longVal(item);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    /** 指定主题域下的资产ID（报表卡片按主题域过滤） */
    public List<Long> assetIdsForDomains(Collection<Long> domainIds) {
        if (domainIds == null || domainIds.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        Object raw = getData("/api/catalog/report-data/asset-ids-for-domains?domainIds=" + join(domainIds));
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = longVal(item);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    /** 批量资产分类路径（报表卡片展示） */
    public Map<Long, Map<String, Object>> assetCategoryPaths(Collection<Long> assetIds) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;
        Map<String, Object> data = mapData(getData(
                "/api/catalog/report-data/asset-category-paths?assetIds=" + join(assetIds)));
        if (data == null) return result;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Long assetId = longVal(entry.getKey());
            Map<String, Object> value = mapData(entry.getValue());
            if (assetId != null && value != null) result.put(assetId, value);
        }
        return result;
    }

    /** 保存资产在主题域下的类目归属 */
    public void saveAssetCategory(Long assetId, Long subjectDomainId, Long categoryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assetId", assetId);
        body.put("subjectDomainId", subjectDomainId);
        body.put("categoryId", categoryId);
        postData("/api/catalog/report-data/asset-categories", body);
    }

    // ==================== 技术元数据 / 指标 / 知识库 ====================

    /** 指定数据源下已同步的表结构与字段（报表 SQL 生成上下文） */
    public List<CatalogTable> tables(Long dataSourceId) {
        Object data = getData("/api/catalog/report-data/tables?dataSourceId=" + dataSourceId);
        if (data == null) return List.of();
        try {
            List<CatalogTable> tables = MAPPER.convertValue(data, new TypeReference<List<CatalogTable>>() { });
            return tables == null ? List.of() : tables;
        } catch (Exception e) {
            throw new BusinessException("DAM 表结构响应解析失败: " + e.getMessage());
        }
    }

    /** 指定数据源下启用指标（报表 Prompt 口径参考） */
    public List<Map<String, Object>> metrics(Long dataSourceId) {
        return listOfMaps(getData("/api/catalog/report-data/metrics?dataSourceId=" + dataSourceId));
    }

    /** 检索业务知识库（报表 Prompt 口径参考） */
    public Map<String, Object> searchKnowledge(String query, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("limit", limit);
        Map<String, Object> data = mapData(postData("/api/catalog/report-data/knowledge/search", body));
        return data == null ? Map.of() : data;
    }

    // ==================== HTTP 基础设施 ====================

    private Object getData(String path) {
        return data(() -> client.get().uri(path).header("token", token()).retrieve().body(Map.class));
    }

    private Object postData(String path, Map<String, Object> body) {
        return data(() -> client.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("token", token())
                .body(body)
                .retrieve().body(Map.class));
    }

    private Object data(Supplier<Map> call) {
        Map<?, ?> body;
        try {
            body = call.get();
        } catch (RestClientException e) {
            throw new BusinessException("DAM 服务调用失败: " + e.getMessage());
        }
        if (body == null) return null;
        int code = body.get("code") instanceof Number n ? n.intValue() : 200;
        if (code != 200) {
            Object message = body.get("message");
            throw new BusinessException(message == null ? "DAM 服务调用失败" : String.valueOf(message));
        }
        return body.get("data");
    }

    private Map<String, Object> mapData(Object data) {
        if (!(data instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object data) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(data instanceof List<?> list)) return result;
        for (Object item : list) {
            Map<String, Object> row = mapData(item);
            if (row != null) result.add(row);
        }
        return result;
    }

    private String join(Collection<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) continue;
            if (!sb.isEmpty()) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
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

    private String token() {
        try {
            String token = StpUtil.getTokenValue();
            return token == null ? "" : token;
        } catch (Exception e) {
            return "";
        }
    }
}
