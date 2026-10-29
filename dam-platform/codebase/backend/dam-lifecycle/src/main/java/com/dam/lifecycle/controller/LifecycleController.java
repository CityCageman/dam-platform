package com.dam.lifecycle.controller;

import com.dam.common.vo.Result;
import com.dam.lifecycle.entity.DamDestructionRecord;
import com.dam.lifecycle.entity.DamLifecyclePolicy;
import com.dam.lifecycle.service.LifecycleService;
import com.dam.lifecycle.service.StorageTieringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生命周期管理接口
 * <p>
 * 提供：
 * <ul>
 *   <li>生命周期策略（metadata_lifecycle_policy）的增查改</li>
 *   <li>数据销毁记录（metadata_destruction_record）的创建与审批</li>
 *   <li>存储分层策略的执行与分布统计</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    /** 生命周期策略/销毁记录服务 */
    private final LifecycleService lifecycleService;

    /** 存储分层策略执行服务 */
    private final StorageTieringService storageTieringService;

    /**
     * 查询全部生命周期策略
     *
     * @return 策略列表
     */
    @GetMapping("/policies")
    public Result<List<DamLifecyclePolicy>> getAllPolicies() {
        return Result.success(lifecycleService.getAllPolicies());
    }

    /**
     * 新增生命周期策略（自动补全审计字段）
     */
    @PostMapping("/policies")
    public Result<DamLifecyclePolicy> createPolicy(@RequestBody DamLifecyclePolicy policy) {
        return Result.success(lifecycleService.createPolicy(policy));
    }

    /**
     * 更新生命周期策略
     */
    @PutMapping("/policies")
    public Result<DamLifecyclePolicy> updatePolicy(@RequestBody DamLifecyclePolicy policy) {
        return Result.success(lifecycleService.updatePolicy(policy));
    }

    // ==================== 销毁记录 ====================

    /**
     * 查询数据销毁记录列表（按创建时间倒序）
     *
     * @return 销毁记录列表
     */
    @GetMapping("/destructions")
    public Result<List<DamDestructionRecord>> getDestructionRecords() {
        return Result.success(lifecycleService.getDestructionRecords());
    }

    /**
     * 新增数据销毁申请（自动补全审计字段与销毁单号）
     */
    @PostMapping("/destructions")
    public Result<DamDestructionRecord> createDestruction(@RequestBody DamDestructionRecord record) {
        return Result.success(lifecycleService.createDestruction(record));
    }

    /**
     * 审批数据销毁申请
     *
     * @param id     销毁记录ID
     * @param record 审批信息（status：1-批准，其余-拒绝；remark：审批意见）
     */
    @PutMapping("/destructions/{id}/approve")
    public Result<Void> approveDestruction(@PathVariable Long id, @RequestBody DamDestructionRecord record) {
        // 根据 status 解析审批结果并更新记录
        lifecycleService.approveDestruction(id, record.getStatus() != null && record.getStatus() == 1 ? "approved" : "rejected",
                record.getRemark());
        return Result.success();
    }

    // ==================== 存储分层 ====================

    /**
     * 执行全部启用的存储分层策略（自动归档/销毁）
     *
     * @return 执行结果统计（归档数、销毁数、通知数）
     */
    @PostMapping("/tiering/execute")
    public Result<Map<String, Object>> executeTiering() {
        return Result.success(storageTieringService.executeAllPolicies());
    }

    /**
     * 获取各存储层级（热/温/冷/过期）的资产分布
     *
     * @return 分层分布统计
     */
    @GetMapping("/tiering/distribution")
    public Result<Map<String, Object>> getTierDistribution() {
        return Result.success(storageTieringService.getTierDistribution());
    }
}
