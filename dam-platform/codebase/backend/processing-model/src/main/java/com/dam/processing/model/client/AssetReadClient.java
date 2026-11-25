package com.dam.processing.model.client;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 资产 API 读取资产与字段，禁止直接访问 dam_dam 表。 */
@Component
public class AssetReadClient {

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetReadClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** 查询资产详情及字段；返回 asset/fields 两个节点。 */
    public Map<String, Object> detail(Long assetId) {
        if (assetId == null) throw new BusinessException(400, "资产ID不能为空");
        Map<?, ?> body = client.get().uri("/api/catalog/assets/{id}", assetId)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> result)) throw new BusinessException(404, "资产不存在：" + assetId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asset", result.get("asset") instanceof Map<?, ?> a ? a : Map.of());
        out.put("fields", result.get("fields") instanceof List<?> f ? f : List.of());
        return out;
    }

    /** 查询可对比资产列表（最多 500 条）。 */
    public List<Map<String, Object>> comparableAssets() {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/search?page=1&pageSize=500")
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        Object records = data instanceof Map<?, ?> m ? m.get("records") : null;
        if (!(records instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> m)) continue;
            Object id = m.get("id");
            Object name = m.get("assetName");
            if (id == null || name == null) continue;
            result.add(Map.of("id", id, "assetName", name, "tableName", name, "fieldCount", 0));
        }
        return result;
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
