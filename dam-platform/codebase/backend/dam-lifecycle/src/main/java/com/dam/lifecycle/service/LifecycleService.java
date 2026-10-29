package com.dam.lifecycle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.lifecycle.entity.DamDestructionRecord;
import com.dam.lifecycle.entity.DamLifecyclePolicy;
import com.dam.lifecycle.repository.DamDestructionRecordMapper;
import com.dam.lifecycle.repository.DamLifecyclePolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 生命周期管理服务
 * <p>
 * 负责生命周期策略（metadata_lifecycle_policy）与数据销毁记录（metadata_destruction_record）的
 * 增查改、销毁审批等业务逻辑；写入时自动补全审计字段（created_by/updated_by 非空约束）。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class LifecycleService {

    /** 生命周期策略 Mapper */
    private final DamLifecyclePolicyMapper policyMapper;

    /** 数据销毁记录 Mapper */
    private final DamDestructionRecordMapper destructionRecordMapper;

    /**
     * 查询全部生命周期策略（返回前填充前端兼容字段）
     *
     * @return 策略列表
     */
    public List<DamLifecyclePolicy> getAllPolicies() {
        // 查询全部策略（无过滤条件）
        List<DamLifecyclePolicy> list = policyMapper.selectList(null);
        for (DamLifecyclePolicy p : list) {
            fillPolicyCompat(p);
        }
        return list;
    }

    /**
     * 填充生命周期策略前端兼容字段（warmDays←coldDays、retentionDays←archiveDays、archiveAction←autoExecute）
     *
     * @param p 策略实体（兼容字段不持久化）
     */
    private void fillPolicyCompat(DamLifecyclePolicy p) {
        if (p == null) return;
        if (p.getWarmDays() == null) p.setWarmDays(p.getColdDays());
        if (p.getRetentionDays() == null) p.setRetentionDays(p.getArchiveDays());
        if (p.getArchiveAction() == null) p.setArchiveAction(Boolean.TRUE.equals(p.getAutoExecute()) ? "archive" : "notify");
        if (p.getAssetType() == null) p.setAssetType(p.getTargetType());
    }

    /**
     * 新增生命周期策略（自动补全审计字段，兼容前端旧字段名）
     * <p>
     * 兼容映射：warmDays→coldDays、retentionDays→archiveDays、archiveAction→autoExecute。
     *
     * @param policy 策略实体
     * @return 创建后的策略（含自增ID）
     */
    @Transactional
    public DamLifecyclePolicy createPolicy(DamLifecyclePolicy policy) {
        // 兼容前端旧字段名
        if (policy.getColdDays() == null && policy.getWarmDays() != null) policy.setColdDays(policy.getWarmDays());
        if (policy.getArchiveDays() == null && policy.getRetentionDays() != null) policy.setArchiveDays(policy.getRetentionDays());
        if (policy.getAutoExecute() == null && "archive".equals(policy.getArchiveAction())) policy.setAutoExecute(Boolean.TRUE);
        if (policy.getTargetType() == null) policy.setTargetType(policy.getAssetType());
        // 获取当前时间并补全审计字段（表审计字段非空）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        if (policy.getCreatedBy() == null) policy.setCreatedBy("system");
        if (policy.getUpdatedBy() == null) policy.setUpdatedBy("system");
        policyMapper.insert(policy);
        return policy;
    }

    /**
     * 更新生命周期策略
     *
     * @param policy 策略实体（按 id 更新）
     * @return 更新后的策略
     */
    @Transactional
    public DamLifecyclePolicy updatePolicy(DamLifecyclePolicy policy) {
        // 按主键更新非空字段
        policyMapper.updateById(policy);
        return policy;
    }

    // ==================== 销毁记录 ====================

    /**
     * 查询数据销毁记录（按创建时间倒序）
     *
     * @return 销毁记录列表
     */
    public List<DamDestructionRecord> getDestructionRecords() {
        // 按创建时间倒序查询全部销毁记录
        return destructionRecordMapper.selectList(
                new LambdaQueryWrapper<DamDestructionRecord>().orderByDesc(DamDestructionRecord::getCreatedAt));
    }

    /**
     * 新增数据销毁申请（自动补全单号、状态与审计字段）
     *
     * @param record 销毁申请实体
     * @return 创建后的销毁记录（含自增ID）
     */
    @Transactional
    public DamDestructionRecord createDestruction(DamDestructionRecord record) {
        // 默认状态：0-待审批
        if (record.getStatus() == null) record.setStatus(0);
        // 自动生成销毁单号（时间戳取模，保证唯一性）
        if (record.getDestructionNo() == null) {
            record.setDestructionNo("DES-" + System.currentTimeMillis() % 1000000);
        }
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setCreatedBy("system");
        record.setUpdatedBy("system");
        destructionRecordMapper.insert(record);
        return record;
    }

    /**
     * 审批数据销毁申请
     *
     * @param id      销毁记录ID
     * @param status  审批结果（approved-批准，其余-拒绝）
     * @param comment 审批意见（写入 remark）
     */
    @Transactional
    public void approveDestruction(Long id, String status, String comment) {
        // 查询待审批的销毁记录
        DamDestructionRecord record = destructionRecordMapper.selectById(id);
        if (record != null) {
            // 更新状态：1-已批准，2-已拒绝
            record.setStatus("approved".equalsIgnoreCase(status) ? 1 : 2);
            record.setRemark(comment);
            record.setApprovedAt(java.time.LocalDateTime.now());
            record.setUpdatedAt(java.time.LocalDateTime.now());
            record.setUpdatedBy("system");
            destructionRecordMapper.updateById(record);
        }
    }
}
