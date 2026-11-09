package com.dam.operations.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

        // 4. 工单状态分布
        // 资产价值分布不在看板使用，且原来会逐资产查询使用统计，已从看板聚合中移除
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
        LocalDate startMonth = now.minusMonths(11).withDayOfMonth(1);
        LocalDateTime start = startMonth.atStartOfDay();

        // 一次聚合查询，避免 12 个月 × 2 张表共 24 次 COUNT
        Map<String, Long> assetByMonth = groupedCount(assetMapper, "asset_info", "created_at", start);
        Map<String, Long> ticketByMonth = groupedCount(ticketMapper, "ticket_order", "created_at", start);

        List<String> months = new ArrayList<>();
        List<Long> assetCounts = new ArrayList<>();
        List<Long> ticketCounts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate month = startMonth.plusMonths(i);
            String label = month.getYear() + "." + String.format("%02d", month.getMonthValue());
            months.add(label);
            assetCounts.add(assetByMonth.getOrDefault(label, 0L));
            ticketCounts.add(ticketByMonth.getOrDefault(label, 0L));
        }

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("months", months);
        trend.put("assetCounts", assetCounts);
        trend.put("ticketCounts", ticketCounts);
        return trend;
    }

    /** PostgreSQL/TBase 按月聚合，返回 YYYY.MM -> count */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Long> groupedCount(com.baomidou.mybatisplus.core.mapper.BaseMapper mapper,
                                           String tableName, String timeColumn, LocalDateTime start) {
        QueryWrapper<Object> qw = new QueryWrapper<>();
        qw.select("to_char(date_trunc('month', " + timeColumn + "), 'YYYY.MM') AS month",
                        "COUNT(*) AS cnt")
                .ge(timeColumn, start)
                .groupBy("date_trunc('month', " + timeColumn + ")");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = mapper.selectMaps((com.baomidou.mybatisplus.core.conditions.Wrapper) qw);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object month = row.get("month");
            Object cnt = row.get("cnt");
            if (month != null && cnt instanceof Number n) result.put(String.valueOf(month), n.longValue());
        }
        return result;
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
        // 一次按状态分组，替代 4 次 COUNT
        Map<Integer, Long> statusMap = new HashMap<>();
        List<Map<String, Object>> statusRows = ticketMapper.selectMaps(
                new QueryWrapper<DamTicket>().select("status", "COUNT(*) AS cnt").groupBy("status"));
        for (Map<String, Object> row : statusRows) {
            if (row.get("status") instanceof Number s && row.get("cnt") instanceof Number c) {
                statusMap.put(s.intValue(), c.longValue());
            }
        }
        long pending = statusMap.getOrDefault(0, 0L);
        long processing = statusMap.getOrDefault(1, 0L);
        long completed = statusMap.getOrDefault(2, 0L);
        long rejected = statusMap.getOrDefault(3, 0L);

        // 按类型一次分组，替代 3 次 COUNT
        Map<String, Long> typeMap = new HashMap<>();
        List<Map<String, Object>> typeRows = ticketMapper.selectMaps(
                new QueryWrapper<DamTicket>().select("ticket_type", "COUNT(*) AS cnt").groupBy("ticket_type"));
        for (Map<String, Object> row : typeRows) {
            if (row.get("ticket_type") != null && row.get("cnt") instanceof Number c) {
                typeMap.put(String.valueOf(row.get("ticket_type")), c.longValue());
            }
        }
        long dataAccess = typeMap.getOrDefault("data_access", 0L);
        long qualityIssue = typeMap.getOrDefault("quality_issue", 0L);
        long changeRequest = typeMap.getOrDefault("change_request", 0L);

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
        List<Map<String, Object>> rows = assetMapper.selectMaps(
                new QueryWrapper<DamAsset>().select("AVG(quality_score) AS avg_score")
                        .isNotNull("quality_score"));
        if (rows.isEmpty() || rows.get(0).get("avg_score") == null) return 0D;
        return ((Number) rows.get(0).get("avg_score")).doubleValue();
    }

    /**
     * 估算活跃用户数（最近30天有工单的申请人去重统计）
     *
     * @return 活跃用户数
     */
    private long estimateActiveUsers() {
        // 最近30天有工单的申请人去重，直接在数据库聚合
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Object> objs = ticketMapper.selectObjs(
                new QueryWrapper<DamTicket>().select("COUNT(DISTINCT applicant_id)")
                        .ge("created_at", since));
        return objs.isEmpty() || objs.get(0) == null ? 0L : ((Number) objs.get(0)).longValue();
    }
}
