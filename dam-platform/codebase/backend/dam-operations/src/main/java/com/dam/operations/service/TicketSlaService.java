package com.dam.operations.service;

import com.dam.operations.entity.DamTicket;
import com.dam.operations.repository.DamTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单SLA计时服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlaService {

    private final DamTicketMapper ticketMapper;

    /**
     * 根据优先级设置SLA截止时间
     * high=4h, medium=24h, low=72h
     */
    public LocalDateTime calculateSlaDeadline(String priority) {
        LocalDateTime now = LocalDateTime.now();
        if ("high".equals(priority)) return now.plusHours(4);
        if ("medium".equals(priority)) return now.plusHours(24);
        if ("low".equals(priority)) return now.plusHours(72);
        return now.plusHours(24); // 默认24h
    }

    /**
     * 获取所有工单的SLA状态
     */
    public List<Map<String, Object>> getSlaStatuses() {
        List<DamTicket> tickets = ticketMapper.selectList(null);
        return tickets.stream()
                .map(this::getTicketSlaStatus)
                .toList();
    }

    /**
     * 获取单个工单的SLA状态
     */
    public Map<String, Object> getTicketSlaStatus(DamTicket ticket) {
        Map<String, Object> status = new HashMap<>();
        status.put("ticketId", ticket.getId());
        status.put("ticketNo", ticket.getTicketNo());
        status.put("priority", ticket.getPriority());
        status.put("status", ticket.getStatus());

        if (ticket.getExpectedCompleteTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            boolean isOverdue = now.isAfter(ticket.getExpectedCompleteTime());
            long remainingMinutes = ChronoUnit.MINUTES.between(now, ticket.getExpectedCompleteTime());
            long remainingHours = ChronoUnit.HOURS.between(now, ticket.getExpectedCompleteTime());

            status.put("slaDeadline", ticket.getExpectedCompleteTime());
            status.put("isOverdue", isOverdue);
            status.put("remainingMinutes", Math.abs(remainingMinutes));
            status.put("remainingHours", Math.abs(remainingHours));
            status.put("slaStatus", getSlaStatusText(isOverdue, remainingHours, ticket.getStatus()));
        } else {
            status.put("slaDeadline", null);
            status.put("isOverdue", false);
            status.put("slaStatus", "未设置");
        }
        return status;
    }

    /**
     * 生成 SLA 状态文案（已完成/已超期/即将超期/紧急/正常）
     *
     * @param isOverdue      是否已超期
     * @param remainingHours 剩余小时数（绝对值）
     * @param ticketStatus   工单状态编码（2-已完成，3-已驳回）
     * @return SLA 状态文案
     */
    private String getSlaStatusText(boolean isOverdue, long remainingHours, Integer ticketStatus) {
        // 2-已完成 3-已驳回
        if (ticketStatus != null && (ticketStatus == 2 || ticketStatus == 3)) {
            return "已完成";
        }
        if (isOverdue) return "已超期";
        if (remainingHours <= 1) return "即将超期";
        if (remainingHours <= 4) return "紧急";
        return "正常";
    }
}
