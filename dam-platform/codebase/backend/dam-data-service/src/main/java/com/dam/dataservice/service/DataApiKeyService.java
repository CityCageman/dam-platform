package com.dam.dataservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.dataservice.dto.DataApiKeyCreateRequest;
import com.dam.dataservice.dto.DataApiKeyCreateResult;
import com.dam.dataservice.dto.DataApiKeyView;
import com.dam.dataservice.entity.DataApiKey;
import com.dam.dataservice.repository.DataApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 数据服务 API Key 管理服务
 * <p>
 * 负责 API Key 的创建、列表与撤销，以及 OpenAPI 请求的 Key 鉴权。
 * <ul>
 *   <li>Key 格式为 {@code dam_} + 32 字节 SecureRandom 的 Base64URL 编码；</li>
 *   <li>数据库只保存 Key 的 SHA-256 十六进制摘要与 12 位展示前缀，明文只在创建
 *       响应中返回一次；</li>
 *   <li>鉴权只接受 {@code status=ACTIVE} 且未过期的 Key，统一返回相同的 401
 *       提示，避免泄露 Key 是否存在；</li>
 *   <li>{@code last_used_at} 采用 best-effort 更新，失败只记录日志，不影响鉴权。</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataApiKeyService {

    /** 生效状态 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 已撤销状态 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 散列算法 */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** Key 固定前缀 */
    private static final String KEY_PREFIX = "dam_";

    /** 随机部分字节数（至少 32 字节） */
    private static final int KEY_RANDOM_BYTES = 32;

    /** 展示前缀长度（不超过 key_prefix varchar(16)） */
    private static final int PREFIX_LENGTH = 12;

    /** Key 名称最大长度 */
    private static final int MAX_NAME_LENGTH = 100;

    /** 订阅方最大长度 */
    private static final int MAX_SUBSCRIBER_LENGTH = 100;

    /** 允许接受的原始 Key 最大长度，避免超长 Header 消耗散列资源 */
    private static final int MAX_RAW_KEY_LENGTH = 512;

    /** 生成 Key 的最大重试次数（仅用于极小概率的摘要碰撞） */
    private static final int MAX_GENERATE_ATTEMPTS = 3;

    /** 空格分隔的时间格式 */
    private static final DateTimeFormatter SPACE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** API Key Mapper */
    private final DataApiKeyMapper apiKeyMapper;

    /** 安全随机源 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建 API Key
     * <p>
     * {@code subscriber} 为空时使用当前登录用户；明文 Key 仅在返回值中出现一次。
     *
     * @param request  创建请求
     * @param operator 当前登录用户（创建人，同时作为默认 subscriber）
     * @return 创建结果（含明文 Key 与主键）
     */
    public DataApiKeyCreateResult create(DataApiKeyCreateRequest request, String operator) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String name = requireText(request.getName(), "name", MAX_NAME_LENGTH);
        String defaultSubscriber = trim(operator);
        String subscriber = trim(request.getSubscriber());
        if (subscriber == null || subscriber.isEmpty()) {
            subscriber = defaultSubscriber;
        }
        subscriber = requireText(subscriber, "subscriber", MAX_SUBSCRIBER_LENGTH);
        // 无管理员角色体系时只允许为自己签发，防止普通用户替他人创建 Key 获取订阅
        if (defaultSubscriber == null || !defaultSubscriber.equals(subscriber)) {
            throw new BusinessException(403, "只能为自己创建 API Key");
        }
        LocalDateTime expiresAt = parseExpiresAt(request.getExpiresAt());

        String createdBy = trim(operator);
        if (createdBy == null || createdBy.isEmpty()) {
            createdBy = "system";
        } else if (createdBy.length() > MAX_SUBSCRIBER_LENGTH) {
            createdBy = createdBy.substring(0, MAX_SUBSCRIBER_LENGTH);
        }

        for (int attempt = 1; attempt <= MAX_GENERATE_ATTEMPTS; attempt++) {
            String plainKey = generateKey();
            LocalDateTime now = LocalDateTime.now();
            DataApiKey entity = new DataApiKey();
            entity.setKeyPrefix(prefixOf(plainKey));
            entity.setKeyHash(sha256Hex(plainKey));
            entity.setSubscriber(subscriber);
            entity.setName(name);
            entity.setStatus(STATUS_ACTIVE);
            entity.setExpiresAt(expiresAt);
            entity.setCreatedAt(now);
            entity.setCreatedBy(createdBy);
            try {
                apiKeyMapper.insert(entity);
                return new DataApiKeyCreateResult(entity.getId(), plainKey,
                        entity.getKeyPrefix(), entity.getName(), entity.getSubscriber(),
                        entity.getStatus(), entity.getExpiresAt());
            } catch (DuplicateKeyException e) {
                // key_hash 唯一约束冲突（概率极低），重新生成；不记录 Key 本身
                log.warn("API Key 摘要冲突，重新生成: attempt={}", attempt);
            }
        }
        throw new BusinessException(500, "API Key 生成失败，请重试");
    }

    /**
     * 查询全部 API Key（仅返回安全展示字段）
     *
     * @return API Key 视图列表，按创建时间倒序
     */
    public List<DataApiKeyView> list(String subscriber) {
        String owner = requireText(subscriber, "subscriber", MAX_SUBSCRIBER_LENGTH);
        return apiKeyMapper.selectList(new LambdaQueryWrapper<DataApiKey>()
                        .eq(DataApiKey::getSubscriber, owner)
                        .orderByDesc(DataApiKey::getCreatedAt)
                        .orderByDesc(DataApiKey::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 撤销 API Key（幂等）
     *
     * @param id 主键
     * @return 撤销后的 API Key 视图
     */
    @Transactional
    public DataApiKeyView revoke(Long id, String subscriber) {
        if (id == null) {
            throw new BusinessException(400, "API Key ID 不能为空");
        }
        String owner = requireText(subscriber, "subscriber", MAX_SUBSCRIBER_LENGTH);
        DataApiKey entity = apiKeyMapper.selectById(id);
        // 不区分不存在与不属于当前用户，避免枚举他人的 Key ID
        if (entity == null || !owner.equals(trim(entity.getSubscriber()))) {
            throw new BusinessException(404, "API Key 不存在");
        }
        if (!STATUS_REVOKED.equalsIgnoreCase(trim(entity.getStatus()))) {
            // 只更新 status，避免用旧快照覆盖并发更新的 last_used_at
            apiKeyMapper.update(null, new LambdaUpdateWrapper<DataApiKey>()
                    .eq(DataApiKey::getId, id)
                    .set(DataApiKey::getStatus, STATUS_REVOKED));
            entity.setStatus(STATUS_REVOKED);
        }
        return toView(entity);
    }

    /**
     * 校验明文 API Key 并返回对应订阅方
     * <p>
     * 只做 SHA-256 摘要查询，不保存也不输出明文；无效、已撤销、已过期与不存在
     * 统一返回 401，避免调用方枚举 Key 状态。
     *
     * @param rawKey 请求头中的明文 API Key
     * @return 订阅方
     * @throws BusinessException Key 无效/已撤销/已过期时抛出 code=401
     */
    public String authenticate(String rawKey) {
        String key = trim(rawKey);
        if (key == null || key.isEmpty() || key.length() > MAX_RAW_KEY_LENGTH) {
            throw invalidKey();
        }
        DataApiKey entity = apiKeyMapper.selectByKeyHash(sha256Hex(key));
        if (entity == null
                || !STATUS_ACTIVE.equalsIgnoreCase(trim(entity.getStatus()))) {
            throw invalidKey();
        }
        if (entity.getExpiresAt() != null
                && !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidKey();
        }
        String subscriber = trim(entity.getSubscriber());
        if (subscriber == null || subscriber.isEmpty()) {
            throw invalidKey();
        }
        touchLastUsed(entity.getId());
        return subscriber;
    }

    /**
     * best-effort 更新最近使用时间；失败不影响鉴权与 API 响应
     *
     * @param id API Key 主键
     */
    private void touchLastUsed(Long id) {
        if (id == null) {
            return;
        }
        try {
            apiKeyMapper.updateLastUsedAt(id, LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("API Key 最近使用时间更新失败: id={}, errorType={}",
                    id, e.getClass().getSimpleName());
        }
    }

    /**
     * 转换为仅含安全展示字段的视图
     *
     * @param entity API Key
     * @return 视图
     */
    private DataApiKeyView toView(DataApiKey entity) {
        return new DataApiKeyView(entity.getKeyPrefix(), entity.getName(),
                entity.getSubscriber(), entity.getStatus(), entity.getExpiresAt(),
                entity.getLastUsedAt());
    }

    /**
     * 生成明文 Key：{@code dam_} + 32 字节安全随机数的 Base64URL 编码
     *
     * @return 明文 Key
     */
    private String generateKey() {
        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * 截取展示前缀
     *
     * @param plainKey 明文 Key
     * @return 最长 12 位的前缀
     */
    private String prefixOf(String plainKey) {
        return plainKey.substring(0, Math.min(PREFIX_LENGTH, plainKey.length()));
    }

    /**
     * 计算 SHA-256 十六进制摘要
     *
     * @param value 明文 Key
     * @return 64 位小写十六进制摘要
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 解析过期时间，支持空格/ISO-8601 日期时间（含时区偏移）与纯日期
     *
     * @param raw 原始文本
     * @return 过期时间；空值返回 null
     */
    private LocalDateTime parseExpiresAt(String raw) {
        String value = trim(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 尝试带时区偏移的 ISO-8601
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 尝试其它格式
        }
        try {
            return LocalDateTime.parse(value, SPACE_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 尝试纯日期
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(400,
                    "expiresAt 格式不正确，支持 yyyy-MM-dd HH:mm:ss、ISO-8601 或 yyyy-MM-dd");
        }
    }

    /**
     * 构造统一的无权提示，避免泄露 Key 状态
     *
     * @return 401 业务异常
     */
    private BusinessException invalidKey() {
        return new BusinessException(401, "API Key 无效或已过期");
    }

    /**
     * 校验必填文本并返回去空格后的值
     *
     * @param value     原始值
     * @param fieldName 字段名
     * @param maxLength 最大长度
     * @return 去空格后的值
     */
    private String requireText(String value, String fieldName, int maxLength) {
        String text = trim(value);
        if (text == null || text.isEmpty()) {
            throw new BusinessException(400, fieldName + " 不能为空");
        }
        if (text.length() > maxLength) {
            throw new BusinessException(400,
                    fieldName + " 长度不能超过 " + maxLength);
        }
        return text;
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
