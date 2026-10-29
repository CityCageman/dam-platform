package com.dam.operations.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.repository.DamTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户行为分析服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorService {

    private final DamTicketMapper ticketMapper;

    /**
     * 获取用户活跃度分析
     */
    public Map<String, Object> getUserActivityAnalysis() {
        // 最近30天的工单数据
        LocalDateTime since30 = LocalDateTime.now().minusDays(30);
        List<DamTicket> recentTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<DamTicket>()
                        .ge(DamTicket::getCreatedAt, since30));

        // 活跃用户统计（按申请人）
        Map<Long, List<DamTicket>> byCreator = recentTickets.stream()
                .filter(t -> t.getApplicantId() != null)
                .collect(Collectors.groupingBy(DamTicket::getApplicantId));

        int activeCreators = byCreator.size();
        int highFreqCreators = (int) byCreator.values().stream()
                .filter(list -> list.size() >= 5).count();

        // 工单创建趋势（按天）
        Map<LocalDate, Long> dailyTrend = recentTickets.stream()
                .filter(t -> t.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate(),
                        Collectors.counting()));

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dates.add(date.toString());
            counts.add(dailyTrend.getOrDefault(date, 0L));
        }

        // 操作类型分布
        Map<String, Long> actionDistribution = recentTickets.stream()
                .collect(Collectors.groupingBy(
                        t -> Optional.ofNullable(t.getTicketType()).orElse("unknown"),
                        Collectors.counting()));

        // 工单处理时效（从创建到完成的平均时长，小时）
        List<DamTicket> completedTickets = recentTickets.stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 2
                        && t.getCreatedAt() != null && t.getActualCompleteTime() != null)
                .toList();

        double avgProcessHours = completedTickets.stream()
                .mapToLong(t -> java.time.Duration.between(t.getCreatedAt(), t.getActualCompleteTime()).toHours())
                .average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeUsers", activeCreators);
        result.put("highFrequencyUsers", highFreqCreators);
        result.put("totalActions", recentTickets.size());
        result.put("dailyTrend", Map.of("dates", dates, "counts", counts));
        result.put("actionDistribution", actionDistribution);
        result.put("avgProcessHours", Math.round(avgProcessHours * 10) / 10.0);
        result.put("period", "30天");
        return result;
    }
}
