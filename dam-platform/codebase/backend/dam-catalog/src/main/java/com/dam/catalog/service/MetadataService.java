package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.*;
import com.dam.catalog.repository.*;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据聚合服务
 * <p>
 * 负责按资产维度聚合展示技术元数据（字段/扩展属性）、业务元数据（标签/评价/收藏/血缘/质量）
 * 与关联元数据，扩展属性读写 metadata_management.extend_props JSONB。
 * 注意：新表字段通过资产多态外键（source_metadata_id=metadata_table.id）关联，
 * 不再使用 asset_id 列。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class MetadataService {

    /** 资产 Mapper */
    private final DamAssetMapper assetMapper;

    /** 字段 Mapper */
    private final DamFieldMapper fieldMapper;

    /** 管理元数据 Mapper（extend_props） */
    private final DamMetadataExtMapper metadataExtMapper;

    /** 采集记录 Mapper */
    private final DamMetadataCollectionMapper collectionMapper;

    /** 数据源 Mapper */
    private final DamDataSourceMapper dataSourceMapper;

    /** 标签 Mapper */
    private final DamTagMapper tagMapper;

    /** 资产标签关联 Mapper */
    private final DamAssetTagMapper assetTagMapper;

    /** 评价 Mapper */
    private final DamRatingMapper ratingMapper;

    /** 收藏 Mapper */
    private final DamFavoriteMapper favoriteMapper;

    /** 血缘 Mapper */
    private final DamLineageMapper lineageMapper;

    /** 质量规则 Mapper */
    private final DamQualityRuleMapper qualityRuleMapper;

    /** 质量检测结果 Mapper */
    private final DamQualityCheckMapper qualityCheckMapper;

    /**
     * 获取某个资产/表的完整技术元数据（按6大类别组织）
     *
     * @param assetId 资产ID
     * @return 技术元数据 Map（asset/fields/extended）
     */
    public Map<String, Object> getTechnicalMetadata(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asset", asset);

        // 1. 基础结构与定义 - 字段列表（通过资产多态外键关联 metadata_column）
        List<DamField> fields = asset.getSourceMetadataId() != null ? fieldMapper.selectList(
                new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId())
                        .orderByAsc(DamField::getId)) : Collections.emptyList();
        result.put("fields", fields);

        // 2-6. 扩展属性（按类别分组，读取 metadata_management.extend_props）
        result.put("extended", loadExtByCategory(assetId));
        return result;
    }

    /** 从 metadata_management.extend_props 加载扩展属性（按类别分组） */
    private Map<String, List<Map<String, Object>>> loadExtByCategory(Long assetId) {
        Map<String, List<Map<String, Object>>> extByCategory = new LinkedHashMap<>();
        Long tableId = resolveMetadataTableId(assetId);
        if (tableId == null) return extByCategory;
        List<DamMetadataExt> mgmtList = metadataExtMapper.selectList(
                new LambdaQueryWrapper<DamMetadataExt>().eq(DamMetadataExt::getTableId, tableId).last("LIMIT 1"));
        if (mgmtList.isEmpty()) return extByCategory;
        String extendProps = mgmtList.getFirst().getExtendProps();
        if (StrUtil.isBlank(extendProps)) return extByCategory;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> props = mapper.readValue(extendProps,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            for (Map.Entry<String, Object> e : props.entrySet()) {
                Object v = e.getValue();
                if (v instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> cm = (java.util.Map<String, Object>) v;
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (Map.Entry<String, Object> kv : cm.entrySet()) {
                        items.add(Map.of("key", kv.getKey(), "value", String.valueOf(kv.getValue()), "sortOrder", 0));
                    }
                    extByCategory.put(e.getKey(), items);
                } else {
                    extByCategory.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                            .add(Map.of("key", e.getKey(), "value", String.valueOf(v), "sortOrder", 0));
                }
            }
        } catch (Exception ex) {
            // 忽略解析异常
        }
        return extByCategory;
    }

    /** 解析资产对应的技术元数据表ID（优先 source_metadata_id，兼容 tableName 过渡字段） */
    private Long resolveMetadataTableId(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) return null;
        if (asset.getSourceMetadataId() != null) return asset.getSourceMetadataId();
        return null;
    }

    /** 读取资产扩展属性原始 JSON */
    private String loadExtendPropsJson(Long tableId) {
        List<DamMetadataExt> list = metadataExtMapper.selectList(
                new LambdaQueryWrapper<DamMetadataExt>().eq(DamMetadataExt::getTableId, tableId).last("LIMIT 1"));
        if (list.isEmpty()) return null;
        return list.getFirst().getExtendProps();
    }

    /**
     * 获取业务元数据：标签、评价、所有者、关联数据
     */
    public Map<String, Object> getBusinessMetadata(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");

        Map<String, Object> result = new LinkedHashMap<>();

        // 标签
        List<DamAssetTag> relations = assetTagMapper.selectList(
                new LambdaQueryWrapper<DamAssetTag>().eq(DamAssetTag::getAssetId, assetId));
        if (!relations.isEmpty()) {
            List<Long> tagIds = relations.stream().map(DamAssetTag::getTagId).collect(Collectors.toList());
            result.put("tags", tagMapper.selectByIds(tagIds));
        } else {
            result.put("tags", Collections.emptyList());
        }

        // 评价
        List<DamRating> ratings = ratingMapper.selectList(
                new LambdaQueryWrapper<DamRating>().eq(DamRating::getAssetId, assetId));
        result.put("ratings", ratings);

        // 平均评分
        double avgScore = ratings.stream().mapToInt(DamRating::getRatingScore).average().orElse(0);
        result.put("avgRating", Math.round(avgScore * 10) / 10.0);

        // 收藏数
        long favCount = favoriteMapper.selectCount(
                new LambdaQueryWrapper<DamFavorite>().eq(DamFavorite::getAssetId, assetId));
        result.put("favoriteCount", favCount);

        // 血缘分（上下游数量）
        long upstreamCount = lineageMapper.selectCount(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getTargetId, assetId));
        long downstreamCount = lineageMapper.selectCount(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getSourceId, assetId));
        result.put("upstreamCount", upstreamCount);
        result.put("downstreamCount", downstreamCount);

        // 质量规则数
        long qualityRuleCount = qualityRuleMapper.selectCount(
                new LambdaQueryWrapper<DamQualityRule>().eq(DamQualityRule::getTargetTableId, assetId));
        result.put("qualityRuleCount", qualityRuleCount);

        return result;
    }

    /**
     * 获取跨模块关联数据：血缘、质量检测结果
     */
    public Map<String, Object> getRelatedMetadata(Long assetId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 血缘 - 上游
        List<DamLineage> upstream = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getTargetId, assetId));
        result.put("upstreamLineage", upstream.stream().map(l -> Map.of(
                "sourceId", l.getSourceId(),
                "sourceAssetId", l.getSourceId(), // 前端兼容字段
                "transformRule", l.getTransformRule(),
                "transformSql", l.getTransformRule(), // 前端兼容字段
                "sourceSystem", l.getSourceSystem()
        )).collect(Collectors.toList()));

        // 血缘 - 下游
        List<DamLineage> downstream = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getSourceId, assetId));
        result.put("downstreamLineage", downstream.stream().map(l -> Map.of(
                "targetId", l.getTargetId(),
                "targetAssetId", l.getTargetId(), // 前端兼容字段
                "transformRule", l.getTransformRule(),
                "transformSql", l.getTransformRule(), // 前端兼容字段
                "sourceSystem", l.getSourceSystem()
        )).collect(Collectors.toList()));

        // 质量规则
        List<DamQualityRule> rules = qualityRuleMapper.selectList(
                new LambdaQueryWrapper<DamQualityRule>().eq(DamQualityRule::getTargetTableId, assetId));
        result.put("qualityRules", rules.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleCode", r.getRuleCode());
            m.put("ruleName", r.getRuleCode()); // 前端兼容字段（新表无 rule_name）
            m.put("ruleType", r.getRuleType());
            m.put("severity", r.getSeverity());
            m.put("status", r.getStatus());
            // 最新检测结果
            DamQualityCheck latest = qualityCheckMapper.selectOne(
                    new LambdaQueryWrapper<DamQualityCheck>()
                            .eq(DamQualityCheck::getRuleId, r.getId())
                            .orderByDesc(DamQualityCheck::getCheckTime)
                            .last("LIMIT 1"));
            if (latest != null) {
                m.put("passRate", latest.getPassRate());
                m.put("lastScore", latest.getPassRate()); // 前端兼容字段
                m.put("lastCheckTime", latest.getCheckTime());
                m.put("lastStatus", latest.getResultStatus());
            }
            return m;
        }).collect(Collectors.toList()));

        return result;
    }

    /**
     * 保存扩展技术元数据属性（合并到 metadata_management.extend_props JSONB）
     */
    @Transactional
    public DamMetadataExt saveExtProperty(Long assetId, String category, String propKey, String propValue) {
        Long tableId = resolveMetadataTableId(assetId);
        if (tableId == null) {
            throw new BusinessException("资产未关联技术元数据，无法保存扩展属性");
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> root = parseProps(loadExtendPropsJson(tableId));
        @SuppressWarnings("unchecked")
        Map<String, Object> cat = (Map<String, Object>) root.computeIfAbsent(category, k -> new java.util.HashMap<>());
        cat.put(propKey, propValue);
        persistProps(tableId, root);

        DamMetadataExt ext = new DamMetadataExt();
        ext.setTableId(tableId);
        ext.setAssetId(assetId);
        ext.setCategory(category);
        ext.setPropKey(propKey);
        ext.setPropValue(propValue);
        try {
            ext.setExtendProps(mapper.writeValueAsString(root));
        } catch (Exception ignored) {}
        return ext;
    }

    /**
     * 批量保存扩展属性
     */
    @Transactional
    public void saveExtProperties(Long assetId, String category, List<Map<String, String>> props) {
        Long tableId = resolveMetadataTableId(assetId);
        if (tableId == null) {
            throw new BusinessException("资产未关联技术元数据，无法保存扩展属性");
        }
        Map<String, Object> root = parseProps(loadExtendPropsJson(tableId));
        Map<String, Object> cat = new java.util.HashMap<>();
        for (Map<String, String> prop : props) {
            String key = prop.get("key");
            String value = prop.get("value");
            if (StrUtil.isBlank(key)) continue;
            cat.put(key, value);
        }
        root.put(category, cat);
        persistProps(tableId, root);
    }

    /**
     * 删除扩展属性（按 assetId+category+propKey 定位）
     */
    @Transactional
    public void deleteExtProperty(Long extId) {
        // 兼容旧接口：按 ID 删除已无对应表，这里仅记录日志
        logDeleteExt(extId);
    }

    /** 解析扩展属性 JSON 字符串为 Map */
    private Map<String, Object> parseProps(String json) {
        if (StrUtil.isBlank(json)) return new java.util.HashMap<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    /** 持久化扩展属性到 metadata_management.extend_props */
    private void persistProps(Long tableId, Map<String, Object> root) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        DamMetadataExt existing = metadataExtMapper.selectOne(
                new LambdaQueryWrapper<DamMetadataExt>().eq(DamMetadataExt::getTableId, tableId).last("LIMIT 1"));
        try {
            String json = mapper.writeValueAsString(root);
            if (existing != null) {
                existing.setExtendProps(json);
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                existing.setUpdatedBy("system");
                metadataExtMapper.updateById(existing);
            } else {
                DamMetadataExt ext = new DamMetadataExt();
                ext.setTableId(tableId);
                ext.setExtendProps(json);
                ext.setStatus(1);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                ext.setCreatedAt(now);
                ext.setUpdatedAt(now);
                ext.setCreatedBy("system");
                ext.setUpdatedBy("system");
                metadataExtMapper.insert(ext);
            }
        } catch (Exception e) {
            throw new BusinessException("扩展属性保存失败: " + e.getMessage());
        }
    }

    private void logDeleteExt(Long extId) {
        // 旧接口兼容：扩展属性已合并至 metadata_management.extend_props，无独立删除入口
    }

    /**
     * 获取可供浏览的资产列表（用于元数据管理页面左侧）
     */
    public List<DamDataSource> getMetadataDataSources() {
        return dataSourceMapper.selectList(
                new LambdaQueryWrapper<DamDataSource>().orderByAsc(DamDataSource::getCreatedAt));
    }

    /**
     * 获取数据源下的采集记录
     */
    public List<DamMetadataCollection> getCollectionsByDataSource(Long dataSourceId) {
        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) return Collections.emptyList();
        return collectionMapper.selectList(
                new LambdaQueryWrapper<DamMetadataCollection>()
                        .eq(DamMetadataCollection::getDatasourceId, dataSourceId)
                        .orderByDesc(DamMetadataCollection::getCreatedAt));
    }
}
