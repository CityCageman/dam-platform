package com.dam.lifecycle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.lifecycle.entity.DamDestructionRecord;
import com.dam.lifecycle.entity.DamLifecyclePolicy;
import com.dam.lifecycle.repository.DamDestructionRecordMapper;
import com.dam.lifecycle.repository.DamLifecyclePolicyMapper;
import com.dam.operations.entity.DamUsageStats;
import com.dam.operations.repository.DamUsageStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 存储分层策略执行服务
 * 根据生命周期策略对资产进行冷/热/温分层
 * 自动执行归档/销毁动作
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageTieringService {

    private final DamLifecyclePolicyMapper policyMapper;
    private final DamAssetMapper assetMapper;
    private final DamUsageStatsMapper usageStatsMapper;
    private final DamDestructionRecordMapper destructionRecordMapper;

    /**
     * 执行所有启用的生命周期策略
     */
    @Transactional
    public Map<String, Object> executeAllPolicies() {
        List<DamLifecyclePolicy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<DamLifecyclePolicy>().eq(DamLifecyclePolicy::getStatus, 1));

        int archived = 0, notified = 0, destroyed = 0;
        List<Map<String, Object>> actions = new ArrayList<>();

        for (DamLifecyclePolicy policy : policies) {
            // 查找匹配该策略的资产
            List<DamAsset> targetAssets = findTargetAssets(policy);

            for (DamAsset asset : targetAssets) {
                Map<String, Object> action = evaluateAsset(asset, policy);
                if (action != null) {
                    actions.add(action);
                    String actionType = (String) action.get("action");
                    switch (actionType) {
                        case "archive" -> archived++;
                        case "destroy" -> { destroyed++; executeDestroy(asset, policy); }
                        case "notify" -> notified++;
                    }
                }
            }
        }

        log.info("存储分层执行完成: 归档{}，销毁{}，通知{}", archived, destroyed, notified);
        return Map.of(
                "archived", archived,
                "destroyed", destroyed,
                "notified", notified,
                "totalActions", actions.size(),
                "actions", actions
        );
    }

    /**
     * 查找匹配策略的资产
     */
    private List<DamAsset> findTargetAssets(DamLifecyclePolicy policy) {
        LambdaQueryWrapper<DamAsset> wrapper = new LambdaQueryWrapper<>();
        // 只处理已注册/已发布的资产
        wrapper.in(DamAsset::getStatus, 1);
        return assetMapper.selectList(wrapper);
    }

    /**
     * 评估单个资产的存储分层状态
     */
    private Map<String, Object> evaluateAsset(DamAsset asset, DamLifecyclePolicy policy) {
        // 获取最近的使用统计
        DamUsageStats stats = getLatestStats(asset.getId());

        LocalDate now = LocalDate.now();
        LocalDate createdAt = asset.getCreatedAt() != null ? asset.getCreatedAt().toLocalDate() : now;
        long ageDays = ChronoUnit.DAYS.between(createdAt, now);

        // 根据最后访问时间和策略判断分层
        String tier;
        String action = null;
        String reason;

        if (policy.getArchiveDays() != null && ageDays > policy.getArchiveDays()) {
            // 超过归档期限
            tier = "过期";
            action = "archive";
            reason = String.format("超过归档期限%d天(已%d天)", policy.getArchiveDays(), ageDays);
        } else if (policy.getColdDays() != null && ageDays > policy.getColdDays()) {
            tier = "冷数据";
            action = "notify";
            reason = String.format("超过冷数据期限%d天(已%d天)", policy.getColdDays(), ageDays);
        } else if (policy.getHotDays() != null && ageDays > policy.getHotDays()) {
            tier = "温数据";
            reason = String.format("超过热数据期限%d天(已%d天)", policy.getHotDays(), ageDays);
        } else {
            return null; // 热数据无需处理
        }

        // 检查是否有最近访问，如果有则降级处理
        if (stats != null && stats.getQueryCount() != null && stats.getQueryCount() > 100) {
            tier = "活跃-" + tier;
            action = "notify"; // 仍活跃的数据只通知不操作
            reason = "虽到期但访问频繁，仅通知";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", asset.getId());
        result.put("assetName", asset.getAssetName());
        result.put("assetType", asset.getAssetTypeId());
        result.put("tier", tier);
        result.put("action", action);
        result.put("reason", reason);
        result.put("ageDays", ageDays);
        result.put("policyName", policy.getPolicyName());
        return result;
    }

    /**
     * 执行销毁操作
     */
    private void executeDestroy(DamAsset asset, DamLifecyclePolicy policy) {
        // 创建销毁记录
        DamDestructionRecord record = new DamDestructionRecord();
        record.setDestructionNo("DES-" + System.currentTimeMillis() % 1000000);
        record.setTargetType("ASSET");
        record.setTargetId(asset.getId());
        record.setTargetName(asset.getAssetName());
        record.setDestructionMethod("logical");
        record.setDestructionReason("生命周期策略[" + policy.getPolicyName() + "]自动触发");
        record.setStatus(0); // 待审批
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setCreatedBy("system");
        record.setUpdatedBy("system");
        destructionRecordMapper.insert(record);

        // 标记资产状态
        asset.setStatus(0);
        assetMapper.updateById(asset);
        log.info("资产[{}]已标记为归档，等待销毁审批", asset.getAssetName());
    }

    /**
     * 获取资产最近一次的使用统计记录
     *
     * @param assetId 资产ID
     * @return 最近的使用统计（无记录时返回 null）
     */
    private DamUsageStats getLatestStats(Long assetId) {
        // 按统计时间倒序取最近一条
        List<DamUsageStats> list = usageStatsMapper.selectList(
                new LambdaQueryWrapper<DamUsageStats>()
                        .eq(DamUsageStats::getAssetId, assetId)
                        .orderByDesc(DamUsageStats::getStatDate)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 获取各存储层级的资产分布
     */
    public Map<String, Object> getTierDistribution() {
        List<DamLifecyclePolicy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<DamLifecyclePolicy>().eq(DamLifecyclePolicy::getStatus, 1));
        List<DamAsset> allAssets = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>().in(DamAsset::getStatus, 1));

        long hot = 0, warm = 0, cold = 0, expired = 0;
        LocalDate now = LocalDate.now();

        for (DamAsset asset : allAssets) {
            if (asset.getCreatedAt() == null) continue;
            long ageDays = ChronoUnit.DAYS.between(asset.getCreatedAt().toLocalDate(), now);

            // 使用默认策略判断
            boolean isExpired = policies.stream().anyMatch(p ->
                    p.getArchiveDays() != null && ageDays > p.getArchiveDays());
            boolean isCold = policies.stream().anyMatch(p ->
                    p.getColdDays() != null && ageDays > p.getColdDays());

            if (isExpired) expired++;
            else if (isCold) cold++;
            else if (policies.stream().anyMatch(p -> p.getHotDays() != null && ageDays > p.getHotDays())) warm++;
            else hot++;
        }

        return Map.of(
                "hot", hot, "warm", warm,
                "cold", cold, "expired", expired,
                "total", allAssets.size()
        );
    }
}
