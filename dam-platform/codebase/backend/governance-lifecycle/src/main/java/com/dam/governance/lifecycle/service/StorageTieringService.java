package com.dam.governance.lifecycle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.governance.lifecycle.client.AssetLifecycleClient;
import com.dam.governance.lifecycle.entity.DamDestructionRecord;
import com.dam.governance.lifecycle.entity.DamLifecycleExecution;
import com.dam.governance.lifecycle.entity.DamLifecyclePolicy;
import com.dam.governance.lifecycle.repository.DamDestructionRecordMapper;
import com.dam.governance.lifecycle.repository.DamLifecycleExecutionMapper;
import com.dam.governance.lifecycle.repository.DamLifecyclePolicyMapper;
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
    private final AssetLifecycleClient assetClient;
    private final DamDestructionRecordMapper destructionRecordMapper;
    /** 执行记录（可选表，未迁移时降级） */
    private final DamLifecycleExecutionMapper executionMapper;

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
            List<Map<String, Object>> targetAssets = findTargetAssets(policy);

            for (Map<String, Object> asset : targetAssets) {
                Map<String, Object> action = evaluateAsset(asset, policy);
                if (action != null) {
                    actions.add(action);
                    String actionType = (String) action.get("action");
                    saveExecution(policy, action, actionType);
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
    private List<Map<String, Object>> findTargetAssets(DamLifecyclePolicy policy) {
        return assetClient.assets().stream()
                .filter(a -> a.get("status") instanceof Number n && n.intValue() == 1)
                .toList();
    }

    /**
     * 评估单个资产的存储分层状态
     */
    private Map<String, Object> evaluateAsset(Map<String, Object> asset, DamLifecyclePolicy policy) {
        // 获取最近的使用统计
        Map<String, Object> stats = getLatestStats(number(asset.get("id")));

        LocalDate now = LocalDate.now();
        LocalDate createdAt = date(asset.get("createdAt"), now);
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
        long queryCount = number(stats == null ? null : stats.get("queryCount")) == null ? 0 : number(stats.get("queryCount"));
        if (queryCount > 100) {
            tier = "活跃-" + tier;
            action = "notify"; // 仍活跃的数据只通知不操作
            reason = "虽到期但访问频繁，仅通知";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", number(asset.get("id")));
        result.put("assetName", asset.get("assetName"));
        result.put("assetType", asset.get("assetTypeId"));
        result.put("tier", tier);
        result.put("action", action);
        result.put("reason", reason);
        result.put("ageDays", ageDays);
        result.put("policyName", policy.getPolicyName());
        return result;
    }

    /**
     * 保存执行记录（表未迁移时只告警，不影响主流程）
     */
    private void saveExecution(DamLifecyclePolicy policy, Map<String, Object> action, String actionType) {
        try {
            if (executionMapper.tableExists() == null) {
                return;
            }
            DamLifecycleExecution log = new DamLifecycleExecution();
            log.setPolicyId(policy.getId());
            log.setPolicyName(policy.getPolicyName());
            log.setAssetId(action.get("assetId") instanceof Number n ? n.longValue() : null);
            log.setAssetName(String.valueOf(action.get("assetName")));
            log.setTier(String.valueOf(action.get("tier")));
            log.setAction(actionType);
            log.setReason(String.valueOf(action.get("reason")));
            log.setExecutedAt(LocalDateTime.now());
            log.setExecutedBy("system");
            executionMapper.insert(log);
        } catch (Exception e) {
            log.warn("生命周期执行记录写入失败（请确认已执行 v7 迁移）: {}", e.getMessage());
        }
    }

    /**
     * 预览某个策略命中的资产
     *
     * @param policyId 策略ID
     * @return 命中明细与统计
     */
    public Map<String, Object> previewPolicy(Long policyId) {
        DamLifecyclePolicy policy = policyMapper.selectById(policyId);
        if (policy == null) throw new RuntimeException("策略不存在");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> asset : findTargetAssets(policy)) {
            Map<String, Object> action = evaluateAsset(asset, policy);
            if (action != null) rows.add(action);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyName", policy.getPolicyName());
        result.put("total", rows.size());
        result.put("rows", rows);
        return result;
    }

    /**
     * 查询执行记录
     */
    public List<DamLifecycleExecution> getExecutions(Long policyId, String action, Integer limit) {
        try {
            int size = limit == null || limit <= 0 ? 200 : Math.min(limit, 1000);
            LambdaQueryWrapper<DamLifecycleExecution> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(policyId != null, DamLifecycleExecution::getPolicyId, policyId);
            wrapper.eq(action != null && !action.isBlank(), DamLifecycleExecution::getAction, action);
            wrapper.orderByDesc(DamLifecycleExecution::getExecutedAt).last("LIMIT " + size);
            return executionMapper.selectList(wrapper);
        } catch (Exception e) {
            log.warn("执行记录表不存在或查询失败（请确认已执行 v7 迁移）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行销毁操作
     */
    private void executeDestroy(Map<String, Object> asset, DamLifecyclePolicy policy) {
        // 创建销毁记录
        DamDestructionRecord record = new DamDestructionRecord();
        record.setDestructionNo("DES-" + System.currentTimeMillis() % 1000000);
        record.setTargetType("ASSET");
        record.setTargetId(number(asset.get("id")));
        record.setTargetName(String.valueOf(asset.get("assetName")));
        record.setDestructionMethod("logical");
        record.setDestructionReason("生命周期策略[" + policy.getPolicyName() + "]自动触发");
        record.setStatus(0); // 待审批
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setCreatedBy("system");
        record.setUpdatedBy("system");
        destructionRecordMapper.insert(record);

        // 通过 DAM API 标记资产状态
        assetClient.updateStatus(number(asset.get("id")), 0);
        log.info("资产[{}]已标记为归档，等待销毁审批", asset.get("assetName"));
    }

    /**
     * 获取资产最近一次的使用统计记录
     *
     * @param assetId 资产ID
     * @return 最近的使用统计（无记录时返回 null）
     */
    private Map<String, Object> getLatestStats(Long assetId) {
        return assetClient.latestStats(assetId);
    }

    /**
     * 获取各存储层级的资产分布
     */
    public Map<String, Object> getTierDistribution() {
        List<DamLifecyclePolicy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<DamLifecyclePolicy>().eq(DamLifecyclePolicy::getStatus, 1));
        List<Map<String, Object>> allAssets = assetClient.assets().stream()
                .filter(a -> a.get("status") instanceof Number n && n.intValue() == 1).toList();

        long hot = 0, warm = 0, cold = 0, expired = 0;
        LocalDate now = LocalDate.now();

        for (Map<String, Object> asset : allAssets) {
            if (asset.get("createdAt") == null) continue;
            long ageDays = ChronoUnit.DAYS.between(date(asset.get("createdAt"), now), now);

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

    private Long number(Object v) { return v instanceof Number n ? n.longValue() : null; }
    private LocalDate date(Object v, LocalDate fallback) {
        if (v == null) return fallback;
        try { return LocalDate.parse(String.valueOf(v).substring(0, 10)); } catch (Exception e) { return fallback; }
    }
}
