package com.dam.aiworkbench.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 接口简易限流器（单实例内存窗口）
 * <p>
 * 按登录用户（无登录态时按来源 IP）做固定窗口计数，防止 AI 接口被刷、
 * 造成 LLM 费用与线程池耗尽。多实例部署时需换成 Redis 限流。
 *
 * @author DAM Platform
 */
@Component
public class AiRateLimiter {

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * 检查并记录一次请求
     *
     * @param key      用户/IP 键
     * @param limit    窗口内最大次数
     * @param windowMs 窗口毫秒
     */
    public void check(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                throw new BusinessException("AI 请求过于频繁，请稍后再试");
            }
            q.addLast(now);
        }
        if (buckets.size() > 5000) {
            buckets.entrySet().removeIf(e -> {
                Deque<Long> d = e.getValue();
                synchronized (d) {
                    return d.isEmpty() || now - d.peekLast() > windowMs;
                }
            });
        }
    }

    /**
     * 当前请求限流键：优先登录用户，其次 X-Forwarded-For/远端地址
     */
    public String currentKey() {
        try {
            Object id = StpUtil.getLoginIdDefaultNull();
            if (id != null) {
                return "u:" + id;
            }
        } catch (Exception ignored) {
            // 无登录态时降级按 IP
        }
        try {
            String forwarded = SaHolder.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded.split(",")[0].trim();
            }
            String realIp = SaHolder.getRequest().getHeader("X-Real-IP");
            return "ip:" + (realIp != null && !realIp.isBlank() ? realIp : "unknown");
        } catch (Exception e) {
            return "ip:unknown";
        }
    }
}
