package com.dam.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.security.entity.DamDataAccessRequest;
import com.dam.security.repository.DamDataAccessRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据使用申请审批服务
 * <p>
 * 负责数据访问申请（security_access_application）的检索、创建与审批。
 * 状态约定：0-待审批，1-已批准，2-已拒绝（SMALLINT）。
 * 注意：新表申请单号列为 application_no、申请人列为 applicant_id。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataAccessRequestService {

    /** 数据访问申请 Mapper */
    private final DamDataAccessRequestMapper requestMapper;

    /**
     * 分页检索数据访问申请（按申请人/状态过滤）
     *
     * @param applicantId 申请人ID
     * @param status      状态字符串（approved/rejected）
     * @param page        页码
     * @param pageSize    每页条数
     * @return 分页结果
     */
    public PageResult<DamDataAccessRequest> searchRequests(String applicantId, String status,
                                                           Integer page, Integer pageSize) {
        // 构造分页对象
        Page<DamDataAccessRequest> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamDataAccessRequest> wrapper = new LambdaQueryWrapper<>();
        // 按申请人ID过滤
        if (StrUtil.isNotBlank(applicantId)) wrapper.eq(DamDataAccessRequest::getApplicantId, applicantId);
        // 按状态过滤（字符串转整数编码）
        if (StrUtil.isNotBlank(status)) wrapper.eq(DamDataAccessRequest::getStatus, statusToCode(status));
        // 按创建时间倒序
        wrapper.orderByDesc(DamDataAccessRequest::getCreatedAt);
        return PageResult.of(requestMapper.selectPage(p, wrapper));
    }

    /**
     * 创建数据访问申请（默认待审批状态，自动生成单号与审计字段）
     *
     * @param request 申请实体
     * @return 创建后的申请
     */
    @Transactional
    public DamDataAccessRequest createRequest(DamDataAccessRequest request) {
        // 默认状态：0-待审批
        request.setStatus(0);
        // 自动生成申请单号
        if (StrUtil.isBlank(request.getApplicationNo())) {
            request.setApplicationNo("APP-" + System.currentTimeMillis() % 1000000);
        }
        // 补全审计字段
        LocalDateTime now = LocalDateTime.now();
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setCreatedBy("system");
        request.setUpdatedBy("system");
        requestMapper.insert(request);
        return request;
    }

    /**
     * 审批数据访问申请
     *
     * @param id         申请ID
     * @param approved   是否批准（true-批准，false-拒绝）
     * @param approverId 审批人ID
     * @param comment    审批意见
     * @return 更新后的申请
     */
    @Transactional
    public DamDataAccessRequest approveRequest(Long id, boolean approved, Long approverId, String comment) {
        // 查询申请并校验存在
        DamDataAccessRequest request = requestMapper.selectById(id);
        if (request == null) throw new BusinessException("申请不存在");
        // 防止重复审批
        if (request.getStatus() != null && request.getStatus() == 1) {
            throw new BusinessException("申请已处理，不能重复审批");
        }
        // 更新状态（1-批准，2-拒绝）与审批信息
        request.setStatus(approved ? 1 : 2);
        request.setApproverId(approverId != null ? String.valueOf(approverId) : null);
        request.setApproveComment(comment);
        request.setApprovedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        request.setUpdatedBy("system");
        requestMapper.updateById(request);
        return request;
    }

    /**
     * 查询待审批的申请列表（按创建时间倒序）
     *
     * @return 待审批申请列表
     */
    public List<DamDataAccessRequest> getPendingRequests() {
        return requestMapper.selectList(
                new LambdaQueryWrapper<DamDataAccessRequest>()
                        .eq(DamDataAccessRequest::getStatus, 0)
                        .orderByDesc(DamDataAccessRequest::getCreatedAt));
    }

    /**
     * 状态字符串 → 状态编码（0-待审批 1-已批准 2-已拒绝）
     *
     * @param status 状态字符串
     * @return 整数编码，未知状态默认 0
     */
    private Integer statusToCode(String status) {
        if (status == null) return 0;
        return switch (status.toLowerCase()) {
            case "approved" -> 1;
            case "rejected" -> 2;
            default -> 0;
        };
    }
}
