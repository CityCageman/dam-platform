package com.dam.operations.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.entity.DamUsageStats;
import com.dam.operations.repository.DamTicketMapper;
import com.dam.operations.repository.DamUsageStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运营看板数据聚合服务
 * 适配新库：ticket_order.status 为 SMALLINT（0-待处理 1-处理中 2-已完成 3-已驳回）
 * asset_statistics 使用 view_count/query_count/download_count/export_count
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DamAssetMapper assetMapper;
    private final DamTicketMapper ticketMapper;
    private final DamUsageStatsMapper usageStatsMapper;
    private final AssetValuationService valuationService;

    /**
     * 获取运营看板概览数据
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 1. 概览卡片数据
        data.put("overview", getOverviewCards());

        // 2. 月度趋势（最近12个月）
        data.put("monthlyTrend", getMonthlyTrend());

        // 3. 热门资产TOP10
        data.put("hotAssets", getHotAssets(10));

        // 4. 资产价值分布
        data.put("valueDistribution", valuationService.getValueDistribution());

        // 5. 工单状态分布
        data.put("ticketOverview", getTicketOverview());

        return data;
    }

    /**
     * 概览卡片数据
     */
    private Map<String, Object> getOverviewCards() {
        long totalAssets = assetMapper.selectCount(null);
        long totalTickets = ticketMapper.selectCount(null);
        long pendingTickets = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 0));
        long completedTickets = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 2));

        // 平均质量评分 - 从 asset_info.quality_score 计算
        Double avgScore = getAverageScore();

        // 活跃用户数 - 最近30天有工单的申请人去重
        long activeUsers = estimateActiveUsers();

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("totalAssets", totalAssets);
        cards.put("totalTickets", totalTickets);
        cards.put("pendingTickets", pendingTickets);
        cards.put("completedTickets", completedTickets);
        cards.put("avgQualityScore", BigDecimal.valueOf(avgScore).setScale(1, RoundingMode.HALF_UP));
        cards.put("activeUsers", activeUsers);
        return cards;
    }

    /**
     * 月度趋势
     */
    private Map<String, Object> getMonthlyTrend() {
        LocalDate now = LocalDate.now();
        List<String> months = new ArrayList<>();
        List<Long> assetCounts = new ArrayList<>();
        List<Long> ticketCounts = new ArrayList<>();

        for (int i = 11; i >= 0; i--) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            String label = monthStart.getYear() + "." + String.format("%02d", monthStart.getMonthValue());
            months.add(label);

            // 当月创建的资产数
            long assets = assetMapper.selectCount(new LambdaQueryWrapper<DamAsset>()
                    .ge(DamAsset::getCreatedAt, monthStart.atStartOfDay())
                    .lt(DamAsset::getCreatedAt, monthEnd.plusDays(1).atStartOfDay()));
            assetCounts.add(assets);

            // 当月创建的工单数
            long tickets = ticketMapper.selectCount(new LambdaQueryWrapper<DamTicket>()
                    .ge(DamTicket::getCreatedAt, monthStart.atStartOfDay())
                    .lt(DamTicket::getCreatedAt, monthEnd.plusDays(1).atStartOfDay()));
            ticketCounts.add(tickets);
        }

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("months", months);
        trend.put("assetCounts", assetCounts);
        trend.put("ticketCounts", ticketCounts);
        return trend;
    }

    /**
     * 热门资产TOP N（按查询次数排序）
     */
    private List<Map<String, Object>> getHotAssets(int limit) {
        List<DamUsageStats> stats = usageStatsMapper.selectList(
                new LambdaQueryWrapper<DamUsageStats>()
                        .orderByDesc(DamUsageStats::getQueryCount)
                        .last("LIMIT " + limit));

        List<Map<String, Object>> list = new ArrayList<>();
        for (DamUsageStats s : stats) {
            list.add(buildHotAssetItem(s));
        }
        return list;
    }

    /**
     * 组装热门资产条目（查询/浏览/下载/导出次数等使用指标）
     *
     * @param s 使用统计数据
     * @return 热门资产条目 Map
     */
    private Map<String, Object> buildHotAssetItem(DamUsageStats s) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("assetId", s.getAssetId());
        item.put("queryCount", s.getQueryCount() != null ? s.getQueryCount() : 0);
        item.put("viewCount", s.getViewCount() != null ? s.getViewCount() : 0);
        item.put("downloadCount", s.getDownloadCount() != null ? s.getDownloadCount() : 0);
        item.put("exportCount", s.getExportCount() != null ? s.getExportCount() : 0);
        item.put("uniqueUsers", s.getUniqueUsers() != null ? s.getUniqueUsers() : 0);
        return item;
    }

    /**
     * 工单概览
     */
    private Map<String, Object> getTicketOverview() {
        long pending = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 0));
        long processing = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 1));
        long completed = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 2));
        long rejected = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getStatus, 3));

        // 按类型统计
        long dataAccess = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getTicketType, "data_access"));
        long qualityIssue = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getTicketType, "quality_issue"));
        long changeRequest = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>().eq(DamTicket::getTicketType, "change_request"));

        // SLA达标率（实际完成时间 <= 期望完成时间的已完成工单）
        long totalCompleted = completed;
        long slaMet = ticketMapper.selectCount(
                new LambdaQueryWrapper<DamTicket>()
                        .eq(DamTicket::getStatus, 2)
                        .isNotNull(DamTicket::getExpectedCompleteTime)
                        .isNotNull(DamTicket::getActualCompleteTime)
                        .apply("actual_complete_time <= expected_complete_time"));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("byStatus", Map.of(
                "pending", pending, "processing", processing,
                "completed", completed, "rejected", rejected));
        overview.put("byType", Map.of(
                "dataAccess", dataAccess, "qualityIssue", qualityIssue,
                "changeRequest", changeRequest));
        overview.put("slaMetRate", totalCompleted > 0 ?
                BigDecimal.valueOf((double) slaMet / totalCompleted * 100)
                        .setScale(1, RoundingMode.HALF_UP) : 0);
        return overview;
    }

    /**
     * 计算所有资产的平均质量评分（asset_info.quality_score）
     *
     * @return 平均评分（无评分时返回 0）
     */
    private Double getAverageScore() {
        List<DamAsset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>().isNotNull(DamAsset::getQualityScore));
        return assets.stream()
                .map(DamAsset::getQualityScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0);
    }

    /**
     * 估算活跃用户数（最近30天有工单的申请人去重统计）
     *
     * @return 活跃用户数
     */
    private long estimateActiveUsers() {
        // 最近30天有工单的申请人去重
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<DamTicket> recentTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<DamTicket>()
                        .ge(DamTicket::getCreatedAt, since));
        return recentTickets.stream()
                .map(DamTicket::getApplicantId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }
}
