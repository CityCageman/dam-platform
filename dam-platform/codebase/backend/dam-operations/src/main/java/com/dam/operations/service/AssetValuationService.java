package com.dam.operations.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.operations.entity.DamUsageStats;
import com.dam.operations.repository.DamUsageStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资产价值评估服务
 * 评估模型: 访问频次(40%) + 业务价值(40%) + 成本效率(20%)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetValuationService {

    private final DamAssetMapper assetMapper;
    private final DamUsageStatsMapper usageStatsMapper;

    // 权重配置
    private static final double WEIGHT_FREQUENCY = 0.4;
    private static final double WEIGHT_BUSINESS = 0.4;
    private static final double WEIGHT_COST = 0.2;

    /**
     * 评估所有资产的综合价值
     */
    @Transactional
    public List<Map<String, Object>> evaluateAll() {
        List<DamAsset> assets = assetMapper.selectList(null);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> results = new ArrayList<>();

        for (DamAsset asset : assets) {
            Map<String, Object> result = evaluateAsset(asset, today);
            results.add(result);

            // 保存评分到 usage_stats
            saveScore(asset.getId(), result, today);
        }

        log.info("资产价值评估完成，共评估{}个资产", results.size());
        return results;
    }

    /**
     * 评估单个资产价值
     */
    public Map<String, Object> evaluateAsset(DamAsset asset, LocalDate statDate) {
        // 获取最近的使用统计
        DamUsageStats stats = getLatestStats(asset.getId());

        // 1. 访问频次评分 (40%)
        double frequencyScore = calculateFrequencyScore(stats);

        // 2. 业务价值评分 (40%)
        double businessScore = calculateBusinessScore(asset, stats);

        // 3. 成本效率评分 (20%)
        double costScore = calculateCostEfficiencyScore(stats);

        // 综合评分
        double totalScore = frequencyScore * WEIGHT_FREQUENCY
                + businessScore * WEIGHT_BUSINESS
                + costScore * WEIGHT_COST;

        totalScore = BigDecimal.valueOf(totalScore)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", asset.getId());
        result.put("assetName", asset.getAssetName());
        result.put("assetType", asset.getAssetType());
        result.put("department", asset.getDepartment());
        result.put("frequencyScore", BigDecimal.valueOf(frequencyScore).setScale(2, RoundingMode.HALF_UP));
        result.put("businessScore", BigDecimal.valueOf(businessScore).setScale(2, RoundingMode.HALF_UP));
        result.put("costScore", BigDecimal.valueOf(costScore).setScale(2, RoundingMode.HALF_UP));
        result.put("totalScore", totalScore);
        result.put("level", getScoreLevel(totalScore));
        result.put("evaluateDate", statDate != null ? statDate.toString() : LocalDate.now().toString());

        // 详细信息
        Map<String, Object> details = buildDetails(stats);
        result.put("details", details);

        return result;
    }

    /**
     * 组装使用明细（查询/浏览/下载/导出次数与独立用户数）
     *
     * @param stats 使用统计数据（可为 null）
     * @return 明细 Map（无统计时各项默认 0）
     */
    private Map<String, Object> buildDetails(DamUsageStats stats) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("queryCount", stats != null ? stats.getQueryCount() : 0);
        details.put("viewCount", stats != null ? stats.getViewCount() : 0);
        details.put("downloadCount", stats != null ? stats.getDownloadCount() : 0);
        details.put("exportCount", stats != null ? stats.getExportCount() : 0);
        details.put("uniqueUsers", stats != null ? stats.getUniqueUsers() : 0);
        return details;
    }

    /**
     * 访问频次评分: 基于查询次数、浏览、下载、导出
     */
    private double calculateFrequencyScore(DamUsageStats stats) {
        if (stats == null) return 30.0; // 无数据则给基础分

        int queryCount = stats.getQueryCount() != null ? stats.getQueryCount() : 0;
        int viewCount = stats.getViewCount() != null ? stats.getViewCount() : 0;
        int downloadCount = stats.getDownloadCount() != null ? stats.getDownloadCount() : 0;
        int exportCount = stats.getExportCount() != null ? stats.getExportCount() : 0;

        // 加权综合访问量
        double weightedAccess = queryCount * 1.0 + viewCount * 0.5 + downloadCount * 3.0 + exportCount * 2.0;

        // 对数归一化到 0-100
        if (weightedAccess <= 0) return 20.0;
        double score = Math.log10(weightedAccess + 1) / Math.log10(10001) * 100;
        return Math.clamp(score, 0, 100);
    }

    /**
     * 业务价值评分: 基于质量评分、唯一用户数
     */
    private double calculateBusinessScore(DamAsset asset, DamUsageStats stats) {
        double score = 50.0; // 基础分

        if (stats != null && stats.getUniqueUsers() != null) {
            // 独立用户数 → 每个用户贡献1分，最高20分
            score += Math.min(stats.getUniqueUsers(), 20);
        }

        // 资产质量评分（0-100）→ 最高贡献20分
        if (asset.getQualityScore() != null) {
            score += asset.getQualityScore().doubleValue() / 100.0 * 20;
        }

        return Math.clamp(score, 0, 100);
    }

    /**
     * 成本效率评分: 返回基础分（新表无成本字段，固定中等偏上）
     */
    private double calculateCostEfficiencyScore(DamUsageStats stats) {
        return 70.0;
    }

    /**
     * 获取评分等级
     */
    private String getScoreLevel(double score) {
        if (score >= 85) return "高价值";
        if (score >= 70) return "较高价值";
        if (score >= 55) return "中等价值";
        if (score >= 40) return "低价值";
        return "待优化";
    }

    /**
     * 获取最新的使用统计
     */
    private DamUsageStats getLatestStats(Long assetId) {
        List<DamUsageStats> list = usageStatsMapper.selectList(
                new LambdaQueryWrapper<DamUsageStats>()
                        .eq(DamUsageStats::getAssetId, assetId)
                        .orderByDesc(DamUsageStats::getStatDate)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 保存评估分数（asset_statistics 仅持久化统计列，评分实时计算）
     */
    private void saveScore(Long assetId, Map<String, Object> result, LocalDate statDate) {
        DamUsageStats stats = getLatestStats(assetId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (stats == null) {
            stats = new DamUsageStats();
            stats.setAssetId(assetId);
            stats.setStatDate(statDate.atStartOfDay());
            stats.setStatus(1);
            stats.setCreatedAt(now);
            stats.setCreatedBy("system");
            stats.setUpdatedAt(now);
            stats.setUpdatedBy("system");
            usageStatsMapper.insert(stats);
        } else {
            stats.setUpdatedAt(now);
            stats.setUpdatedBy("system");
            usageStatsMapper.updateById(stats);
        }
    }

    /**
     * 获取价值分布统计
     */
    public Map<String, Object> getValueDistribution() {
        List<DamAsset> assets = assetMapper.selectList(null);
        List<Map<String, Object>> evaluations = assets.stream()
                .map(a -> evaluateAsset(a, LocalDate.now()))
                .toList();

        Map<String, Long> distribution = evaluations.stream()
                .collect(Collectors.groupingBy(
                        e -> (String) e.get("level"),
                        Collectors.counting()
                ));

        double avgScore = evaluations.stream()
                .mapToDouble(e -> ((Number) e.get("totalScore")).doubleValue())
                .average().orElse(0);

        Map<String, Object> result = new HashMap<>();
        result.put("distribution", distribution);
        result.put("avgScore", BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        result.put("totalEvaluated", assets.size());
        result.put("topAssets", evaluations.stream()
                .sorted((a, b) -> Double.compare(
                        ((Number) b.get("totalScore")).doubleValue(),
                        ((Number) a.get("totalScore")).doubleValue()))
                .limit(10)
                .toList());
        return result;
    }
}
