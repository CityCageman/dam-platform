package com.dam.catalog.service;

import com.dam.catalog.entity.DamMetadataVersion;
import com.dam.catalog.repository.DamMetadataVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 元数据版本服务
 * <p>
 * 负责元数据版本（metadata_version）的查询与两个版本之间的差异比较，
 * 版本按技术元数据表 ID（table_id）+ 版本号（version_number）定位。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class VersionService {

    /** 元数据版本 Mapper */
    private final DamMetadataVersionMapper metadataVersionMapper;

    /**
     * 获取资产（技术元数据表）的所有版本历史
     *
     * @param assetId 资产ID（语义为 metadata_table.id）
     * @return 版本列表（按变更时间倒序）
     */
    public List<DamMetadataVersion> getVersions(Long assetId) {
        // 按表ID查询并倒序排列
        return metadataVersionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DamMetadataVersion>()
                        .eq(DamMetadataVersion::getTableId, assetId)
                        .orderByDesc(DamMetadataVersion::getChangedAt));
    }

    /**
     * 比较两个版本的差异
     *
     * @param assetId  资产ID（metadata_table.id）
     * @param versionA 版本A
     * @param versionB 版本B
     * @return 比较结果 { versionA, versionB, diffs }
     */
    public Map<String, Object> compareVersions(Long assetId, Integer versionA, Integer versionB) {
        // 按表ID + 版本号定位两个版本记录
        DamMetadataVersion vA = metadataVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DamMetadataVersion>()
                        .eq(DamMetadataVersion::getTableId, assetId)
                        .eq(DamMetadataVersion::getVersionNumber, String.valueOf(versionA)));
        DamMetadataVersion vB = metadataVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DamMetadataVersion>()
                        .eq(DamMetadataVersion::getTableId, assetId)
                        .eq(DamMetadataVersion::getVersionNumber, String.valueOf(versionB)));

        Map<String, Object> result = new HashMap<>();
        result.put("versionA", vA);
        result.put("versionB", vB);

        // 两版本快照均存在时计算差异
        if (vA != null && vB != null && vA.getSnapshotData() != null && vB.getSnapshotData() != null) {
            // 简单比较: 将两个JSON解析后比较字段差异
            List<Map<String, Object>> diffs = computeDiff(vA.getSnapshotData(), vB.getSnapshotData());
            result.put("diffs", diffs);
        } else {
            result.put("diffs", Collections.emptyList());
        }
        return result;
    }

    /**
     * 计算两个快照 JSON 的差异（简化实现：仅展示两个版本的快照摘要）
     *
     * @param jsonA 版本A快照
     * @param jsonB 版本B快照
     * @return 差异列表
     */
    private List<Map<String, Object>> computeDiff(String jsonA, String jsonB) {
        // 简化实现：仅展示两个版本的快照
        List<Map<String, Object>> diffs = new ArrayList<>();
        Map<String, Object> diff = new HashMap<>();
        diff.put("field", "snapshotJson");
        // 超长内容截断展示前200字符
        diff.put("oldValue", jsonA.length() > 200 ? jsonA.substring(0, 200) + "..." : jsonA);
        diff.put("newValue", jsonB.length() > 200 ? jsonB.substring(0, 200) + "..." : jsonB);
        diffs.add(diff);
        return diffs;
    }
}
