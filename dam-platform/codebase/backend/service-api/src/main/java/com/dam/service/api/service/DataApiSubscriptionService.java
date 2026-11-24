package com.dam.service.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.service.api.entity.DataApi;
import com.dam.service.api.entity.DataApiSubscription;
import com.dam.service.api.repository.DataApiMapper;
import com.dam.service.api.repository.DataApiSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据 API 订阅管理服务
 * <p>
 * 负责订阅关系的创建、按 API 查询、订阅状态变更与全局分页查询。
 * 同一 API 与订阅方只允许一条订阅记录（数据库唯一约束 + 服务层预校验）；
 * 订阅状态：PENDING/ACTIVE/REJECTED/DISABLED。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class DataApiSubscriptionService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 合法订阅状态 */
    private static final Set<String> SUBSCRIPTION_STATUSES =
            Set.of("PENDING", "ACTIVE", "REJECTED", "DISABLED");

    /** 数据 API Mapper（用于校验 API 是否存在） */
    private final DataApiMapper dataApiMapper;

    /** 订阅 Mapper */
    private final DataApiSubscriptionMapper subscriptionMapper;

    /**
     * 分页查询订阅列表（支持按 API、订阅方、状态过滤）
     *
     * @param apiId      API ID（可空）
     * @param subscriber 订阅方（可空，模糊匹配）
     * @param status     订阅状态（可空）
     * @param page       页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页结果
     */
    public PageResult<DataApiSubscription> page(Long apiId, String subscriber,
                                                String status, Integer page, Integer pageSize) {
        int current = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, MAX_PAGE_SIZE);
        LambdaQueryWrapper<DataApiSubscription> wrapper = new LambdaQueryWrapper<>();
        if (apiId != null) {
            wrapper.eq(DataApiSubscription::getApiId, apiId);
        }
        String subscriberKeyword = trim(subscriber);
        if (subscriberKeyword != null && !subscriberKeyword.isEmpty()) {
            String matched = subscriberKeyword.length() > 100
                    ? subscriberKeyword.substring(0, 100) : subscriberKeyword;
            wrapper.like(DataApiSubscription::getSubscriber, matched);
        }
        String normalizedStatus = trim(status);
        if (normalizedStatus != null && !normalizedStatus.isEmpty()) {
            wrapper.eq(DataApiSubscription::getStatus, normalizeStatus(normalizedStatus));
        }
        wrapper.orderByDesc(DataApiSubscription::getCreatedAt)
                .orderByDesc(DataApiSubscription::getId);
        return PageResult.of(subscriptionMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 查询指定 API 的订阅列表
     *
     * @param apiId API ID
     * @return 该 API 的订阅列表（按创建时间倒序）
     */
    public List<DataApiSubscription> listByApi(Long apiId) {
        requireApi(apiId);
        return subscriptionMapper.selectList(new LambdaQueryWrapper<DataApiSubscription>()
                .eq(DataApiSubscription::getApiId, apiId)
                .orderByDesc(DataApiSubscription::getCreatedAt)
                .orderByDesc(DataApiSubscription::getId));
    }

    /**
     * 创建订阅
     * <p>
     * 校验订阅方与状态；同一 API + 订阅方重复订阅时抛出业务异常；
     * status 为空时默认 PENDING，等待审批。
     *
     * @param apiId   API ID
     * @param request 订阅请求（仅使用 subscriber/status）
     * @return 创建后的订阅记录
     */
    @Transactional
    public DataApiSubscription create(Long apiId, DataApiSubscription request) {
        requireApi(apiId);
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String subscriber = requireSubscriber(request.getSubscriber());
        String status = normalizeStatusOrDefault(request.getStatus(), "PENDING");

        Long exists = subscriptionMapper.selectCount(new LambdaQueryWrapper<DataApiSubscription>()
                .eq(DataApiSubscription::getApiId, apiId)
                .eq(DataApiSubscription::getSubscriber, subscriber));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "订阅方已订阅该数据 API，请勿重复订阅");
        }

        LocalDateTime now = LocalDateTime.now();
        DataApiSubscription subscription = new DataApiSubscription();
        subscription.setApiId(apiId);
        subscription.setSubscriber(subscriber);
        subscription.setStatus(status);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException e) {
            // 并发订阅时由唯一约束 api_id + subscriber 兜底
            throw new BusinessException(409, "订阅方已订阅该数据 API，请勿重复订阅");
        }
        return subscription;
    }

    /**
     * 更新订阅状态
     *
     * @param id     订阅 ID
     * @param status 目标状态：PENDING/ACTIVE/REJECTED/DISABLED
     * @return 更新后的订阅记录
     */
    @Transactional
    public DataApiSubscription updateStatus(Long id, String status) {
        if (id == null) {
            throw new BusinessException(400, "订阅 ID 不能为空");
        }
        String normalizedStatus = normalizeStatus(status);
        DataApiSubscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null) {
            throw new BusinessException(404, "订阅记录不存在");
        }
        subscription.setStatus(normalizedStatus);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(subscription);
        return subscription;
    }

    /**
     * 校验数据 API 是否存在（未逻辑删除）
     *
     * @param apiId API ID
     */
    private void requireApi(Long apiId) {
        if (apiId == null) {
            throw new BusinessException(400, "API ID 不能为空");
        }
        DataApi api = dataApiMapper.selectById(apiId);
        if (api == null) {
            throw new BusinessException(404, "数据 API 不存在");
        }
    }

    /**
     * 校验订阅方并返回去空格后的值
     *
     * @param subscriber 订阅方
     * @return 去空格后的订阅方
     */
    private String requireSubscriber(String subscriber) {
        String value = trim(subscriber);
        if (value == null || value.isEmpty()) {
            throw new BusinessException(400, "subscriber 不能为空");
        }
        if (value.length() > 100) {
            throw new BusinessException(400, "subscriber 长度不能超过 100");
        }
        return value;
    }

    /**
     * 规范化订阅状态（转大写并校验白名单）
     *
     * @param status 原始状态
     * @return 规范化后的状态
     */
    private String normalizeStatus(String status) {
        String value = trim(status);
        if (value == null || value.isEmpty()) {
            throw new BusinessException(400, "status 不能为空");
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!SUBSCRIPTION_STATUSES.contains(value)) {
            throw new BusinessException(400,
                    "status 不合法，仅支持 PENDING/ACTIVE/REJECTED/DISABLED");
        }
        return value;
    }

    /**
     * 规范化订阅状态，为空时使用默认值
     *
     * @param status       原始状态
     * @param defaultValue 默认状态
     * @return 规范化后的状态
     */
    private String normalizeStatusOrDefault(String status, String defaultValue) {
        String value = trim(status);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return normalizeStatus(value);
    }

    /**
     * 去除首尾空白
     *
     * @param value 原始值
     * @return 去空白后的值；原值为 null 时返回 null
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
