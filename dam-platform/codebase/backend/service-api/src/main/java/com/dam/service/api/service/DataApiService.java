package com.dam.service.api.service;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.security.DataScopeContext;
import com.dam.platform.common.security.CredentialCipher;
import com.dam.platform.common.vo.PageResult;
import com.dam.service.api.dto.DataApiRequest;
import com.dam.service.api.entity.DataApi;
import com.dam.service.api.repository.DataApiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 数据 API 管理服务
 * <p>
 * 负责数据 API 定义的分页查询、创建、更新、逻辑删除以及发布/下线状态流转。
 * 状态机：DRAFT/OFFLINE → PUBLISHED（发布），PUBLISHED → OFFLINE（下线）；
 * 已是 PUBLISHED 的 API 重复发布将抛出业务异常。
 * <p>
 * 创建/更新时对 upstreamConfig 根字段 {@code password} 加密（AES-GCM，
 * {@code ENC1:} 前缀）后落库；查询接口原样返回库中密文，不解密、不输出明文。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataApiService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 合法发布状态 */
    private static final Set<String> API_STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    /** 合法 HTTP 方法 */
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    /** 数据 API Mapper */
    private final DataApiMapper dataApiMapper;

    /** 配置凭据加解密组件（upstreamConfig.password 落库前加密） */
    private final CredentialCipher credentialCipher;

    /**
     * 分页查询数据 API 列表
     *
     * @param keyword  关键字（匹配 apiCode/apiName/path，可空）
     * @param status   发布状态（可空）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<DataApi> page(String keyword, String status, Integer page, Integer pageSize) {
        int current = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, MAX_PAGE_SIZE);
        LambdaQueryWrapper<DataApi> wrapper = new LambdaQueryWrapper<>();
        String kw = trim(keyword);
        if (kw != null && !kw.isEmpty()) {
            // 关键字最多保留 100 字符，避免超长 LIKE 影响查询
            String matched = kw.length() > 100 ? kw.substring(0, 100) : kw;
            wrapper.and(w -> w.like(DataApi::getApiCode, matched)
                    .or().like(DataApi::getApiName, matched)
                    .or().like(DataApi::getPath, matched));
        }
        String normalizedStatus = trim(status);
        if (normalizedStatus != null && !normalizedStatus.isEmpty()) {
            wrapper.eq(DataApi::getStatus, normalizeStatus(normalizedStatus));
        }
        if (DataScopeContext.isDepartment()) {
            String dept = DataScopeContext.department();
            if (dept.isBlank()) wrapper.eq(DataApi::getId, -1L);
            else wrapper.and(w -> w.eq(DataApi::getDepartment, dept).or().isNull(DataApi::getDepartment));
        } else if (DataScopeContext.isSelf()) {
            String username = DataScopeContext.username();
            if (username.isBlank()) wrapper.eq(DataApi::getId, -1L);
            else wrapper.eq(DataApi::getCreatedBy, username);
        }
        wrapper.orderByDesc(DataApi::getUpdatedAt).orderByDesc(DataApi::getId);
        return PageResult.of(dataApiMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 查询数据 API 详情
     *
     * @param id API ID
     * @return API 定义
     */
    public DataApi getById(Long id) {
        return requireApi(id);
    }

    /**
     * 创建数据 API
     * <p>
     * 校验 apiCode/apiName/path/method；apiCode 全局唯一（包含已逻辑删除记录，
     * 避免复用编码触发数据库唯一约束异常）；status 为空时默认 DRAFT。
     *
     * @param request 创建请求
     * @return 创建后的 API（含自增 ID）
     */
    @Transactional
    public DataApi create(DataApiRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String apiCode = requireText(request.getApiCode(), "apiCode", 64);
        String apiName = requireText(request.getApiName(), "apiName", 200);
        String path = requireText(request.getPath(), "path", 500);
        if (!path.startsWith("/")) {
            throw new BusinessException(400, "path 必须以 / 开头");
        }
        String method = normalizeMethod(request.getMethod());
        String status = normalizeStatusOrDefault(request.getStatus(), "DRAFT");

        // apiCode 唯一性校验：包含已逻辑删除的记录（数据库唯一约束不区分删除状态）
        if (dataApiMapper.countByApiCodeIncludingDeleted(apiCode) > 0) {
            throw new BusinessException(409, "apiCode 已存在：" + apiCode);
        }

        LocalDateTime now = LocalDateTime.now();
        DataApi api = new DataApi();
        api.setApiCode(apiCode);
        api.setApiName(apiName);
        api.setPath(path);
        api.setMethod(method);
        api.setStatus(status);
        api.setDescription(request.getDescription());
        api.setUpstreamType(trim(request.getUpstreamType()));
        api.setUpstreamConfig(normalizeUpstreamConfig(request.getUpstreamConfig()));
        api.setCreatedAt(now);
        api.setCreatedBy(actorOrDefault(request.getCreatedBy()));
        api.setUpdatedAt(now);
        api.setUpdatedBy(actorOrDefault(request.getUpdatedBy()));
        try {
            dataApiMapper.insert(api);
        } catch (DuplicateKeyException e) {
            // 并发创建时由数据库唯一约束兜底
            throw new BusinessException(409, "apiCode 已存在：" + apiCode);
        }
        return api;
    }

    /**
     * 更新数据 API
     * <p>
     * 不允许修改 apiCode；未传字段保持原值。status 若传入需为合法状态。
     *
     * @param id      API ID
     * @param request 更新请求
     * @return 更新后的 API
     */
    @Transactional
    public DataApi update(Long id, DataApiRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        DataApi api = requireApi(id);

        String apiCode = trim(request.getApiCode());
        if (apiCode != null && !apiCode.isEmpty() && !apiCode.equals(api.getApiCode())) {
            throw new BusinessException(400, "不允许修改 apiCode");
        }
        if (request.getApiName() != null) {
            api.setApiName(requireText(request.getApiName(), "apiName", 200));
        }
        if (request.getPath() != null) {
            String path = requireText(request.getPath(), "path", 500);
            if (!path.startsWith("/")) {
                throw new BusinessException(400, "path 必须以 / 开头");
            }
            api.setPath(path);
        }
        if (request.getMethod() != null) {
            api.setMethod(normalizeMethod(request.getMethod()));
        }
        if (request.getStatus() != null) {
            api.setStatus(normalizeStatusOrDefault(request.getStatus(), api.getStatus()));
        }
        if (request.getDescription() != null) {
            api.setDescription(request.getDescription());
        }
        if (request.getUpstreamType() != null) {
            api.setUpstreamType(trim(request.getUpstreamType()));
        }
        if (request.getUpstreamConfig() != null) {
            api.setUpstreamConfig(normalizeUpstreamConfig(request.getUpstreamConfig()));
        }
        api.setUpdatedAt(LocalDateTime.now());
        String updatedBy = trim(request.getUpdatedBy());
        if (updatedBy != null && !updatedBy.isEmpty()) {
            api.setUpdatedBy(updatedBy);
        }
        dataApiMapper.updateById(api);
        return api;
    }

    /**
     * 逻辑删除数据 API
     *
     * @param id API ID
     */
    @Transactional
    public void delete(Long id) {
        // 先校验存在性，避免删除不存在的记录被静默忽略
        requireApi(id);
        // @TableLogic 自动将 deleted_at 置为 now()
        dataApiMapper.deleteById(id);
    }

    /**
     * 发布数据 API（DRAFT/OFFLINE → PUBLISHED）
     *
     * @param id API ID
     * @return 发布后的 API
     */
    @Transactional
    public DataApi publish(Long id) {
        DataApi api = requireApi(id);
        if ("PUBLISHED".equals(api.getStatus())) {
            throw new BusinessException(409, "数据 API 已发布，不能重复发布");
        }
        if (!"DRAFT".equals(api.getStatus()) && !"OFFLINE".equals(api.getStatus())) {
            throw new BusinessException(409, "仅 DRAFT/OFFLINE 状态的数据 API 可以发布");
        }
        api.setStatus("PUBLISHED");
        touch(api);
        dataApiMapper.updateById(api);
        return api;
    }

    /**
     * 下线数据 API（PUBLISHED → OFFLINE）
     *
     * @param id API ID
     * @return 下线后的 API
     */
    @Transactional
    public DataApi offline(Long id) {
        DataApi api = requireApi(id);
        if (!"PUBLISHED".equals(api.getStatus())) {
            throw new BusinessException(409, "仅 PUBLISHED 状态的数据 API 可以下线");
        }
        api.setStatus("OFFLINE");
        touch(api);
        dataApiMapper.updateById(api);
        return api;
    }

    /**
     * 按 ID 查询未删除的数据 API，不存在时抛出业务异常
     *
     * @param id API ID
     * @return API 定义
     */
    private DataApi requireApi(Long id) {
        if (id == null) {
            throw new BusinessException(400, "API ID 不能为空");
        }
        DataApi api = dataApiMapper.selectById(id);
        if (api == null) {
            throw new BusinessException(404, "数据 API 不存在");
        }
        return api;
    }

    /**
     * 校验必填文本并返回去空格后的值
     *
     * @param value     原始值
     * @param fieldName 字段名（用于错误提示）
     * @param maxLength 最大长度
     * @return 去空格后的值
     */
    private String requireText(String value, String fieldName, int maxLength) {
        String text = trim(value);
        if (text == null || text.isEmpty()) {
            throw new BusinessException(400, fieldName + " 不能为空");
        }
        if (text.length() > maxLength) {
            throw new BusinessException(400, fieldName + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    /**
     * 规范化 HTTP 方法（转大写并校验白名单）
     *
     * @param method 原始方法
     * @return 规范化后的方法
     */
    private String normalizeMethod(String method) {
        String value = requireText(method, "method", 16).toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(value)) {
            throw new BusinessException(400,
                    "method 不合法，仅支持 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS");
        }
        return value;
    }

    /**
     * 规范化发布状态（转大写并校验白名单）
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
        if (!API_STATUSES.contains(value)) {
            throw new BusinessException(400,
                    "status 不合法，仅支持 DRAFT/PUBLISHED/OFFLINE");
        }
        return value;
    }

    /**
     * 规范化发布状态，为空时使用默认值
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
     * 将上游配置转换为合法的 JSON 文本（jsonb）
     * <p>
     * 支持请求体直接传 JSON 对象/数组，也支持传 JSON 字符串；空值统一落库为 NULL。
     * 校验通过后对根字段 {@code password} 加密，再返回待落库的 JSON 文本。
     *
     * @param config 上游配置
     * @return 加密后的 JSON 文本或 null
     */
    private String normalizeUpstreamConfig(Object config) {
        if (config == null) {
            return null;
        }
        String json;
        if (config instanceof String value) {
            json = value.trim();
        } else {
            json = JSONUtil.toJsonStr(config);
        }
        if (json == null || json.isEmpty()) {
            return null;
        }
        if (!JSONUtil.isTypeJSON(json)) {
            throw new BusinessException(400, "upstreamConfig 必须是合法的 JSON");
        }
        return encryptUpstreamPassword(json);
    }

    /**
     * 加密 upstreamConfig 根字段 password
     * <p>
     * 只处理根字段 {@code password}，其余字段值原样保留；空值、非文本值以及已带
     * {@code ENC1:} 前缀的密文不做处理，避免前端回传密文时重复加密。配置不是
     * JSON 对象（如数组）时保持原有落库行为。加密失败时向上抛出，避免把明文
     * 密码静默写入数据库。
     *
     * @param json 已校验合法的配置 JSON
     * @return 加密后的 JSON；无需加密时返回原值
     */
    private String encryptUpstreamPassword(String json) {
        JSONObject config;
        try {
            JSON parsed = JSONUtil.parse(json);
            if (!(parsed instanceof JSONObject object)) {
                return json;
            }
            config = object;
        } catch (RuntimeException e) {
            // 仅记录异常类型，避免把配置内容（含密码）写入日志
            log.warn("upstreamConfig 解析失败，跳过密码加密: errorType={}",
                    e.getClass().getSimpleName());
            return json;
        }
        Object value = config.get("password");
        if (value == null || value instanceof JSONNull) {
            return json;
        }
        String plain = String.valueOf(value);
        if (plain.isBlank() || plain.startsWith(CredentialCipher.PREFIX)) {
            return json;
        }
        config.set("password", credentialCipher.encrypt(plain));
        return config.toString();
    }

    /**
     * 更新审计时间与更新人
     *
     * @param api API 定义
     */
    private void touch(DataApi api) {
        api.setUpdatedAt(LocalDateTime.now());
        String current = trim(api.getUpdatedBy());
        api.setUpdatedBy(current == null || current.isEmpty() ? "system" : current);
    }

    /**
     * 操作人默认值
     *
     * @param actor 操作人
     * @return 非空操作人，缺省为 system
     */
    private String actorOrDefault(String actor) {
        String value = trim(actor);
        return value == null || value.isEmpty() ? "system" : value;
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
