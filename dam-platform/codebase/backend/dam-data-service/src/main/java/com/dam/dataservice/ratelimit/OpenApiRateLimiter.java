package com.dam.dataservice.ratelimit;

import com.dam.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开放 API 基础限流器（单实例内存滑动窗口）
 * <p>
 * 按「subscriber + apiCode」统计最近 60 秒内的调用次数，超过 60 次抛出
 * {@link BusinessException}（code=429）。执行接口与 schema 接口共用同一窗口；
 * 平台登录与 API Key 鉴权得到的 subscriber 相同则共用同一限流桶。
 * <p>
 * 注意：本实现仅在当前 JVM 内计数，<b>多实例部署时不保证全局配额</b>——每个实例
 * 各自允许 60 次/分钟，实际总次数可能随实例数放大。如果需要集群级限流，应替换为
 * Redis（如 Lua 脚本 + ZSET 滑动窗口）或网关层限流。
 * <p>
 * 内存占用通过 {@link #MAX_BUCKETS} 兜底清理：键数量超过阈值时移除窗口已空的
 * 桶，避免长期运行后无界增长。
 *
 * @author DAM Platform
 */
@Component
public class OpenApiRateLimiter {

    /** 每个「订阅方 + API」每分钟允许的最大调用次数 */
    private static final int MAX_REQUESTS_PER_WINDOW = 60;

    /** 滑动窗口长度（毫秒） */
    private static final long WINDOW_MS = 60_000L;

    /** 限流桶数量上限，超过后触发过期清理 */
    private static final int MAX_BUCKETS = 10_000;

    /**
     * 限流桶：key 为 {@code subscriber:apiCode}，value 为窗口内的请求时间戳（升序）
     */
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * 检查并记录一次开放 API 调用
     * <p>
     * 先移除窗口外的历史时间戳，再判断窗口内是否已达上限；未超限时记录本次
     * 调用时间。时间统一使用 {@link System#currentTimeMillis()}，不依赖业务时钟。
     *
     * @param subscriber 订阅方（平台登录用户或 API Key 绑定的订阅方）
     * @param apiCode    API 编码
     * @throws BusinessException 超过限流阈值时抛出 code=429
     */
    public void check(String subscriber, String apiCode) {
        String key = subscriber + ":" + apiCode;
        long now = System.currentTimeMillis();
        Deque<Long> window = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            evictExpired(window, now);
            if (window.size() >= MAX_REQUESTS_PER_WINDOW) {
                long first = window.peekFirst() == null ? now : window.peekFirst();
                long retryAfterMs = Math.max(1L, first + WINDOW_MS - now);
                long retryAfterSeconds = (retryAfterMs + 999L) / 1000L;
                // 只提示重试时间，不返回订阅、上游配置等任何内部信息
                throw new BusinessException(429,
                        "调用过于频繁，请在 " + retryAfterSeconds + " 秒后重试");
            }
            window.addLast(now);
        }
        if (buckets.size() > MAX_BUCKETS) {
            evictIdleBuckets(now);
        }
    }

    /**
     * 移除窗口外的历史请求时间戳
     *
     * @param window 当前桶
     * @param now    当前时间（毫秒）
     */
    private void evictExpired(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
            window.pollFirst();
        }
    }

    /**
     * 清理窗口已空的桶，控制内存占用
     * <p>
     * 仅当桶数量超过阈值时触发；与 {@link #check} 中同一把桶内锁互斥，避免误删
     * 正在写入的窗口。删除后即使并发请求再次创建同名桶，也只会少算一个过期窗口，
     * 不影响限流正确性。
     *
     * @param now 当前时间（毫秒）
     */
    private void evictIdleBuckets(long now) {
        buckets.entrySet().removeIf(entry -> {
            Deque<Long> window = entry.getValue();
            synchronized (window) {
                evictExpired(window, now);
                return window.isEmpty();
            }
        });
    }
}
