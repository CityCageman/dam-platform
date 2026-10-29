package com.dam.operations.controller;

import com.dam.catalog.repository.DamAssetMapper;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.entity.DamTicketComment;
import com.dam.operations.entity.DamUsageStats;
import com.dam.operations.repository.DamTicketMapper;
import com.dam.operations.service.AssetValuationService;
import com.dam.operations.service.DashboardService;
import com.dam.operations.service.OperationsService;
import com.dam.operations.service.TicketSlaService;
import com.dam.operations.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 运营中心接口
 * <p>
 * 提供：
 * <ul>
 *   <li>运营看板（资产规模/质量/热度等统计）</li>
 *   <li>工单管理（ticket_order 的检索/创建/状态流转/指派）</li>
 *   <li>资产价值评估（多维度打分）</li>
 *   <li>工单评论、SLA 监控、用户行为分析</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {

    /** 工单/使用统计服务 */
    private final OperationsService operationsService;

    /** 运营看板服务 */
    private final DashboardService dashboardService;

    /** 资产价值评估服务 */
    private final AssetValuationService assetValuationService;

    /** 工单 SLA 服务 */
    private final TicketSlaService ticketSlaService;

    /** 用户行为分析服务 */
    private final UserBehaviorService userBehaviorService;

    /** 资产 Mapper（跨模块复用 catalog） */
    private final DamAssetMapper assetMapper;

    /** 工单 Mapper */
    private final DamTicketMapper ticketMapper;

    // ==================== 运营看板 ====================

    /**
     * 获取运营看板数据（资产/质量/工单/热度等汇总）
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        return Result.success(dashboardService.getDashboardData());
    }

    /**
     * 获取运营总览统计（兼容接口）
     */
    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(operationsService.getDashboard());
    }

    // ==================== 工单管理 ====================

    /**
     * 分页检索工单（按类型/状态过滤）
     *
     * @param ticketType 工单类型
     * @param status     工单状态（整数编码字符串）
     * @param page       页码
     * @param pageSize   每页条数
     * @return 分页工单结果
     */
    @GetMapping("/tickets")
    public Result<PageResult<DamTicket>> searchTickets(
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(operationsService.searchTickets(ticketType, status, page, pageSize));
    }

    /**
     * 创建工单（自动补全单号与审计字段）
     */
    @PostMapping("/tickets")
    public Result<DamTicket> createTicket(@RequestBody DamTicket ticket) {
        return Result.success(operationsService.createTicket(ticket));
    }

    /**
     * 更新工单状态（含流转记录）
     */
    @PutMapping("/tickets/{id}/status")
    public Result<DamTicket> updateTicketStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(operationsService.updateTicketStatus(id, body.get("status"), body.get("comment")));
    }

    /**
     * 指派工单处理人
     */
    @PutMapping("/tickets/{id}/assign")
    public Result<DamTicket> assignTicket(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.success(operationsService.assignTicket(id, body.get("assigneeId")));
    }

    // ==================== 资产价值评估 ====================

    /**
     * 全量评估所有资产价值（批量打分）
     */
    @PostMapping("/valuation/evaluate-all")
    public Result<List<Map<String, Object>>> evaluateAll() {
        return Result.success(assetValuationService.evaluateAll());
    }

    /**
     * 获取资产价值分布（高/中/低价值区间统计）
     */
    @GetMapping("/valuation/distribution")
    public Result<Map<String, Object>> getValueDistribution() {
        return Result.success(assetValuationService.getValueDistribution());
    }

    /**
     * 评估单个资产价值
     *
     * @param assetId 资产ID
     */
    @GetMapping("/valuation/{assetId}")
    public Result<Map<String, Object>> evaluateAsset(@PathVariable Long assetId) {
        // 先校验资产是否存在
        com.dam.catalog.entity.DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return Result.error("资产不存在");
        }
        return Result.success(assetValuationService.evaluateAsset(asset, java.time.LocalDate.now()));
    }

    // ==================== 使用统计 ====================

    /**
     * 获取资产使用统计数据列表
     */
    @GetMapping("/usage-stats")
    public Result<List<DamUsageStats>> getUsageStats() {
        return Result.success(operationsService.getUsageStats());
    }

    // ==================== 工单评论 ====================

    /**
     * 查询工单评论列表
     */
    @GetMapping("/tickets/{ticketId}/comments")
    public Result<List<DamTicketComment>> getComments(@PathVariable Long ticketId) {
        return Result.success(operationsService.getTicketComments(ticketId));
    }

    /**
     * 添加工单评论
     */
    @PostMapping("/tickets/{ticketId}/comments")
    public Result<DamTicketComment> addComment(
            @PathVariable Long ticketId,
            @RequestBody DamTicketComment comment) {
        // 评论关联到指定工单
        comment.setTicketId(ticketId);
        return Result.success(operationsService.addComment(comment));
    }

    // ==================== SLA管理 ====================

    /**
     * 获取全部工单的 SLA 状态列表
     */
    @GetMapping("/tickets/sla")
    public Result<List<Map<String, Object>>> getSlaStatuses() {
        return Result.success(ticketSlaService.getSlaStatuses());
    }

    /**
     * 获取单个工单的 SLA 状态
     */
    @GetMapping("/tickets/{id}/sla")
    public Result<Map<String, Object>> getTicketSla(@PathVariable Long id) {
        // 先校验工单是否存在
        DamTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            return Result.error("工单不存在");
        }
        return Result.success(ticketSlaService.getTicketSlaStatus(ticket));
    }

    // ==================== 用户行为分析 ====================

    /**
     * 获取用户行为活动分析（浏览/查询/下载等行为统计）
     */
    @GetMapping("/user-behavior")
    public Result<Map<String, Object>> getUserBehavior() {
        return Result.success(userBehaviorService.getUserActivityAnalysis());
    }
}
