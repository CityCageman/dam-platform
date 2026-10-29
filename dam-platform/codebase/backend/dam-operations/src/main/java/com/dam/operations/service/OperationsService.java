package com.dam.operations.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.entity.DamTicketComment;
import com.dam.operations.entity.DamUsageStats;
import com.dam.operations.repository.DamTicketCommentMapper;
import com.dam.operations.repository.DamTicketMapper;
import com.dam.operations.repository.DamUsageStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营中心服务
 * <p>
 * 负责工单（ticket_order）的检索/创建/状态流转/指派、
 * 资产使用统计（asset_statistics）查询与工单评论（ticket_comment）维护。
 * 注意：ticket_order.status 为 SMALLINT（0-待处理，1-处理中，2-已完成，3-已驳回），
 * 外部传入的状态字符串需经 {@link #statusToCode(String)} 转换为整数编码。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class OperationsService {

    /** 工单 Mapper */
    private final DamTicketMapper ticketMapper;

    /** 资产使用统计 Mapper */
    private final DamUsageStatsMapper usageStatsMapper;

    /** 工单评论 Mapper */
    private final DamTicketCommentMapper commentMapper;

    /**
     * 获取运营总览（资产数/工单数/待处理工单数）
     *
     * @return 总览统计 Map
     */
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        // 资产总数（复用 asset_statistics 表行数近似）
        dashboard.put("totalAssets", usageStatsMapper.selectCount(null));
        // 工单总数
        dashboard.put("totalTickets", ticketMapper.selectCount(null));
        // 待处理工单数（status=0）
        dashboard.put("pendingTickets", ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 0)));
        return dashboard;
    }

    /**
     * 分页检索工单（支持按类型/状态过滤，按创建时间倒序）
     *
     * @param ticketType 工单类型（可选）
     * @param status     工单状态字符串（可选，如 completed/processing/rejected）
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页工单结果
     */
    public PageResult<DamTicket> searchTickets(String ticketType, String status,
                                                Integer page, Integer pageSize) {
        // 构造分页对象
        Page<DamTicket> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamTicket> wrapper = new LambdaQueryWrapper<>();
        // 按工单类型过滤
        if (StrUtil.isNotBlank(ticketType)) {
            wrapper.eq(DamTicket::getTicketType, ticketType);
        }
        // 按状态过滤（字符串转整数编码，避免类型不匹配报错）
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(DamTicket::getStatus, statusToCode(status));
        }
        // 按创建时间倒序排列
        wrapper.orderByDesc(DamTicket::getCreatedAt);
        return PageResult.of(ticketMapper.selectPage(p, wrapper));
    }

    /**
     * 创建工单（自动生成单号与审计字段）
     *
     * @param ticket 工单实体
     * @return 创建后的工单（含自增ID）
     */
    @Transactional
    public DamTicket createTicket(DamTicket ticket) {
        // 自动生成工单号（时间戳取模）
        if (ticket.getTicketNo() == null) {
            ticket.setTicketNo("TK-" + System.currentTimeMillis() % 1000000);
        }
        // 默认状态：0-待处理
        if (ticket.getStatus() == null) ticket.setStatus(0);
        // 补全审计字段（非空约束）
        if (ticket.getCreatedBy() == null) ticket.setCreatedBy("system");
        if (ticket.getUpdatedBy() == null) ticket.setUpdatedBy("system");
        ticketMapper.insert(ticket);
        return ticket;
    }

    /**
     * 更新工单状态（状态字符串转整数编码，完成/关闭时记录实际完成时间）
     *
     * @param id      工单ID
     * @param status  目标状态字符串
     * @param comment 状态变更备注（当前版本未持久化流转日志）
     * @return 更新后的工单
     */
    @Transactional
    public DamTicket updateTicketStatus(Long id, String status, String comment) {
        // 查询工单并校验存在
        DamTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) throw new BusinessException("工单不存在");
        // 转换并写入状态编码
        ticket.setStatus(statusToCode(status));
        // 完成/关闭时记录实际完成时间
        if ("completed".equals(status) || "closed".equals(status)) {
            ticket.setActualCompleteTime(LocalDateTime.now());
        }
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /**
     * 状态字符串 → 状态编码（0-待处理 1-处理中 2-已完成 3-已驳回）
     *
     * @param status 状态字符串（completed/processing/rejected 等）
     * @return 对应的整数编码，未知状态默认 0
     */
    private Integer statusToCode(String status) {
        if (status == null) return 0;
        // 按语义映射到 SMALLINT 编码
        return switch (status.toLowerCase()) {
            case "completed", "closed", "resolved" -> 2;
            case "processing", "in_progress", "assigned" -> 1;
            case "rejected", "cancelled" -> 3;
            default -> 0;
        };
    }

    /**
     * 指派工单处理人
     *
     * @param id         工单ID
     * @param assigneeId 处理人用户ID
     * @return 更新后的工单
     */
    @Transactional
    public DamTicket assignTicket(Long id, Long assigneeId) {
        // 查询工单并校验存在
        DamTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) throw new BusinessException("工单不存在");
        // 写入处理人ID
        ticket.setAssigneeId(assigneeId);
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /**
     * 查询最近的使用统计数据（按统计时间倒序，最多100条）
     *
     * @return 使用统计列表
     */
    public List<DamUsageStats> getUsageStats() {
        return usageStatsMapper.selectList(
                new LambdaQueryWrapper<DamUsageStats>()
                        .orderByDesc(DamUsageStats::getStatDate)
                        .last("LIMIT 100"));
    }

    // ==================== 工单评论 ====================

    /**
     * 添加工单评论（自动补全审计字段与默认状态）
     *
     * @param comment 评论实体
     * @return 创建后的评论（含自增ID）
     */
    @Transactional
    public DamTicketComment addComment(DamTicketComment comment) {
        // 补全审计字段与默认状态（审计字段非空约束）
        LocalDateTime now = LocalDateTime.now();
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        if (comment.getCreatedBy() == null) comment.setCreatedBy("system");
        if (comment.getUpdatedBy() == null) comment.setUpdatedBy("system");
        if (comment.getStatus() == null) comment.setStatus(1);
        commentMapper.insert(comment);
        return comment;
    }

    /**
     * 查询指定工单的全部评论（按创建时间升序）
     *
     * @param ticketId 工单ID
     * @return 评论列表
     */
    public List<DamTicketComment> getTicketComments(Long ticketId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<DamTicketComment>()
                        .eq(DamTicketComment::getTicketId, ticketId)
                        .orderByAsc(DamTicketComment::getCreatedAt));
    }
}
