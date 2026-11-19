package com.dam.integration.access.service;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.dam.integration.access.entity.DataAccessSource;
import com.dam.integration.access.model.DataSourceTypeDefinition;
import com.dam.integration.access.repository.DataAccessSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数据接入数据源服务
 * <p>
 * 数据源管理写入口：列表、详情、创建、更新、逻辑删除与连接测试。
 * 密码规则与 catalog 保持一致：接收 RSA 密文（兼容历史明文）后以 {@code ENC:} AES 密文落库；
 * 所有响应均不回传密码，只返回 {@code hasPassword} 掩码标记。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAccessSourceService {

    /** 数据源 Mapper */
    private final DataAccessSourceMapper sourceMapper;

    /** 密码加解密服务 */
    private final AccessCryptoService cryptoService;

    /** JDBC 类型/URL/连接服务 */
    private final DataAccessJdbcService jdbcService;

    /** data_layer 列是否存在（存量表可能没有，懒加载缓存） */
    private volatile Boolean dataLayerColumnPresent;

    /**
     * 查询数据源列表（脱敏）
     *
     * @param keyword 关键字（名称/编码/URL），可为空
     * @param dsType  类型，可为空
     * @param status  状态 0/1，可为空
     * @return 数据源列表
     */
    @Transactional(readOnly = true)
    public List<DataAccessSource> list(String keyword, String dsType, Integer status) {
        validateStatus(status);
        String keywordValue = trimToNull(keyword);
        String typeValue = trimToNull(dsType);
        // 类型别名归一化为内置编码；同时带展示名，兼容历史以展示名存储的数据
        String typeCode = typeValue;
        String typeName = typeValue;
        DataSourceTypeDefinition definition = jdbcService.findType(typeValue);
        if (definition != null) {
            typeCode = definition.code();
            typeName = definition.name();
        }
        List<DataAccessSource> list = sourceMapper.selectActiveList(
                keywordValue, typeCode, typeName, status);
        for (DataAccessSource source : list) {
            fillDerived(source);
        }
        return list;
    }

    /**
     * 查询数据源详情（脱敏）
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @Transactional(readOnly = true)
    public DataAccessSource detail(Long id) {
        DataAccessSource source = requireSource(id);
        fillDerived(source);
        return source;
    }

    /**
     * 创建数据源
     * <p>密码入参为 RSA 密文（或历史明文），落库前统一转换为 {@code ENC:} AES 密文。
     *
     * @param request 数据源配置
     * @return 创建后的数据源（脱敏）
     */
    @Transactional
    public DataAccessSource create(DataAccessSource request) {
        if (request == null) {
            throw new BusinessException(400, "数据源配置不能为空");
        }
        request.setDsName(requireText(request.getDsName(), "数据源名称(dsName)", 100));
        String rawType = requireText(request.getDsType(), "数据源类型(dsType)", 50);
        DataSourceTypeDefinition definition = jdbcService.findType(rawType);
        request.setDsType(definition == null ? rawType : definition.code());
        request.setDsAdapter(requireTextWithDefault(request.getDsAdapter(), request.getDsType(),
                "dsAdapter", 100));
        request.setUsername(requireText(request.getUsername(), "用户名(username)", 100));
        request.setDsIp(trimToNull(request.getDsIp()));
        request.setDsPort(trimToNull(request.getDsPort()));
        request.setConnectionProps(trimToNull(request.getConnectionProps()));
        request.setDescription(trimToNull(request.getDescription()));
        request.setDataLayer(trimToNull(request.getDataLayer()));

        if (request.getDsPort() != null) {
            jdbcService.parsePort(request.getDsPort(),
                    definition == null ? 0 : definition.defaultPort());
        }

        String code = trimToNull(request.getDsCode());
        if (code == null) {
            code = generateCode(request.getDsType());
        }
        requireMaxLen(code, "数据源代码(dsCode)", 100);
        if (sourceMapper.countByCode(code) > 0) {
            throw new BusinessException(409, "数据源代码已存在: " + code);
        }
        request.setDsCode(code);

        String databaseName = trimToNull(request.getDatabaseName());
        String jdbcUrl = trimToNull(request.getJdbcUrl());
        if (jdbcUrl == null) {
            if (definition == null) {
                throw new BusinessException(400,
                        "未知数据源类型: " + request.getDsType() + "，请直接提供 jdbcUrl");
            }
            jdbcUrl = jdbcService.buildJdbcUrl(definition, request.getDsIp(),
                    request.getDsPort(), databaseName);
        }
        requireMaxLen(jdbcUrl, "jdbcUrl", 200);
        request.setJdbcUrl(jdbcUrl);

        String driverClass = trimToNull(request.getDriverClass());
        if (driverClass == null) {
            driverClass = definition != null
                    ? definition.driver() : jdbcService.inferDriver(jdbcUrl);
            if (driverClass == null) {
                throw new BusinessException(400,
                        "无法确定 JDBC 驱动类，请提供 driverClass 或使用内置数据源类型");
            }
        }
        requireMaxLen(driverClass, "driverClass", 200);
        request.setDriverClass(driverClass);

        // 传输密码：RSA 解密（失败按历史明文兼容）→ AES 存储加密
        String rawPassword = request.getPassword();
        if (rawPassword != null && !rawPassword.isEmpty()) {
            request.setPassword(cryptoService.encryptStorage(
                    cryptoService.decryptTransport(rawPassword)));
        } else {
            // password 列为 NOT NULL，空密码落空串；hasPassword 仍为 false
            request.setPassword("");
        }
        requireMaxLen(request.getPassword(), "密码（AES密文）", 100);

        validateStatus(request.getStatus());
        if (request.getStatus() == null) {
            request.setStatus(1);
        }
        if (!supportsDataLayer()) {
            request.setDataLayer(null);
        }
        validateOptionalLengths(request);

        LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        request.setId(null);
        request.setCreatedAt(now);
        request.setCreatedBy(user);
        request.setUpdatedAt(now);
        request.setUpdatedBy(user);
        request.setDeletedAt(null);
        try {
            sourceMapper.insert(request);
        } catch (DuplicateKeyException e) {
            // 并发创建同一 dsCode 时由数据库唯一索引兜底
            throw new BusinessException(409, "数据源代码已存在: " + code);
        }
        fillDerived(request);
        return request;
    }

    /**
     * 更新数据源
     * <p>空密码表示保留原密码；已有 jdbcUrl 优先保留，仅在连接参数变化且未提供 URL 时才重新组装。
     *
     * @param pathId  路径中的数据源 ID（可为空，为空时取请求体 id）
     * @param request 待更新字段（null 表示不修改）
     * @return 更新后的数据源（脱敏）
     */
    @Transactional
    public DataAccessSource update(Long pathId, DataAccessSource request) {
        if (request == null) {
            throw new BusinessException(400, "数据源配置不能为空");
        }
        Long id = pathId != null ? pathId : request.getId();
        DataAccessSource existing = requireSource(id);
        String oldType = existing.getDsType();

        // 仅合并请求中明确提供的字段；password 为空时保留原密码
        if (request.getDsName() != null) {
            existing.setDsName(request.getDsName());
        }
        if (request.getDsCode() != null) {
            existing.setDsCode(request.getDsCode());
        }
        if (request.getDsType() != null) {
            existing.setDsType(request.getDsType());
        }
        if (request.getDsAdapter() != null) {
            existing.setDsAdapter(request.getDsAdapter());
        }
        if (request.getDsIp() != null) {
            existing.setDsIp(request.getDsIp());
        }
        if (request.getDsPort() != null) {
            existing.setDsPort(request.getDsPort());
        }
        if (request.getJdbcUrl() != null && !request.getJdbcUrl().isBlank()) {
            existing.setJdbcUrl(request.getJdbcUrl());
        }
        if (request.getUsername() != null) {
            existing.setUsername(request.getUsername());
        }
        if (request.getDriverClass() != null) {
            existing.setDriverClass(request.getDriverClass());
        }
        if (request.getConnectionProps() != null) {
            existing.setConnectionProps(request.getConnectionProps());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getDataLayer() != null) {
            existing.setDataLayer(request.getDataLayer());
        }

        existing.setDsName(requireText(existing.getDsName(), "数据源名称(dsName)", 100));
        String rawType = requireText(existing.getDsType(), "数据源类型(dsType)", 50);
        DataSourceTypeDefinition definition = jdbcService.findType(rawType);
        existing.setDsType(definition == null ? rawType : definition.code());
        existing.setDsAdapter(requireTextWithDefault(existing.getDsAdapter(), existing.getDsType(),
                "dsAdapter", 100));
        existing.setUsername(requireText(existing.getUsername(), "用户名(username)", 100));
        existing.setDsIp(trimToNull(existing.getDsIp()));
        existing.setDsPort(trimToNull(existing.getDsPort()));
        existing.setConnectionProps(trimToNull(existing.getConnectionProps()));
        existing.setDescription(trimToNull(existing.getDescription()));
        existing.setDataLayer(trimToNull(existing.getDataLayer()));
        existing.setJdbcUrl(trimToNull(existing.getJdbcUrl()));

        String code = requireText(existing.getDsCode(), "数据源代码(dsCode)", 100);
        requireMaxLen(code, "数据源代码(dsCode)", 100);
        if (sourceMapper.countByCodeExcludingId(code, id) > 0) {
            throw new BusinessException(409, "数据源代码已存在: " + code);
        }
        existing.setDsCode(code);

        if (existing.getDsPort() != null) {
            jdbcService.parsePort(existing.getDsPort(),
                    definition == null ? 0 : definition.defaultPort());
        }

        // 驱动类：请求显式提供优先；类型变更或原驱动为空时按类型/URL 补齐
        String requestedDriver = trimToNull(request.getDriverClass());
        String currentDriver = trimToNull(existing.getDriverClass());
        boolean typeChanged = request.getDsType() != null
                && !equalsIgnoreCase(trimToNull(oldType), existing.getDsType());
        if (requestedDriver != null) {
            existing.setDriverClass(requestedDriver);
        } else if (currentDriver == null || (typeChanged && definition != null)) {
            String driver = definition != null
                    ? definition.driver() : jdbcService.inferDriver(existing.getJdbcUrl());
            if (driver == null) {
                throw new BusinessException(400,
                        "无法确定 JDBC 驱动类，请提供 driverClass 或使用内置数据源类型");
            }
            existing.setDriverClass(driver);
        }
        requireMaxLen(existing.getDriverClass(), "driverClass", 200);

        // JDBC URL：请求显式提供优先；已有 URL 优先保留；连接参数变化且未提供 URL 时重新组装
        String requestedUrl = trimToNull(request.getJdbcUrl());
        String currentUrl = trimToNull(existing.getJdbcUrl());
        boolean connectionParamsProvided = request.getDsIp() != null
                || request.getDsPort() != null
                || request.getDatabaseName() != null
                || request.getDsType() != null;
        if (requestedUrl != null) {
            existing.setJdbcUrl(requestedUrl);
        } else if (connectionParamsProvided && definition != null) {
            String databaseName = trimToNull(request.getDatabaseName());
            if (databaseName == null) {
                databaseName = jdbcService.parseDatabaseName(currentUrl);
            }
            boolean hostOrDatabaseChanged = request.getDsIp() != null
                    || request.getDsPort() != null
                    || request.getDatabaseName() != null;
            if (hostOrDatabaseChanged) {
                existing.setJdbcUrl(jdbcService.buildJdbcUrl(definition,
                        existing.getDsIp(), existing.getDsPort(), databaseName));
            }
        }
        if (trimToNull(existing.getJdbcUrl()) == null) {
            if (definition == null) {
                throw new BusinessException(400,
                        "未知数据源类型: " + existing.getDsType() + "，请直接提供 jdbcUrl");
            }
            String databaseName = trimToNull(request.getDatabaseName());
            if (databaseName == null) {
                throw new BusinessException(400,
                        "数据库名/命名空间(databaseName)不能为空，或请直接提供 jdbcUrl");
            }
            existing.setJdbcUrl(jdbcService.buildJdbcUrl(definition,
                    existing.getDsIp(), existing.getDsPort(), databaseName));
        }
        requireMaxLen(existing.getJdbcUrl(), "jdbcUrl", 200);

        // 密码：空表示不修改，保留原密文；非空为 RSA 密文（兼容明文）→ AES 存储
        String rawPassword = request.getPassword();
        if (rawPassword != null && !rawPassword.isEmpty()) {
            existing.setPassword(cryptoService.encryptStorage(
                    cryptoService.decryptTransport(rawPassword)));
            requireMaxLen(existing.getPassword(), "密码（AES密文）", 100);
        }

        if (existing.getStatus() == null) {
            existing.setStatus(1);
        } else {
            validateStatus(existing.getStatus());
        }
        if (!supportsDataLayer()) {
            existing.setDataLayer(null);
        }
        validateOptionalLengths(existing);

        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(currentUser());
        try {
            sourceMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "数据源代码已存在: " + existing.getDsCode());
        }
        fillDerived(existing);
        return existing;
    }

    /**
     * 逻辑删除数据源
     *
     * @param id 数据源 ID
     */
    @Transactional
    public void delete(Long id) {
        DataAccessSource existing = requireSource(id);
        sourceMapper.deleteById(existing.getId());
    }

    /**
     * 测试数据源连接
     * <p>请求含 id/dataSourceId 时读取已存凭据；否则使用临时参数。
     * 密码只经 JDBC Properties 传递，响应与日志均不含密码。
     *
     * @param params 连接参数
     * @return ok/databaseProduct/databaseVersion/driverName/elapsedMs
     */
    public Map<String, Object> testConnection(Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        Long id = parseLong(firstNonNull(safeParams, "id", "dataSourceId"));
        if (id != null) {
            return testStoredConnection(id);
        }

        String type = firstNonNull(safeParams, "dsType", "sourceType", "type");
        DataSourceTypeDefinition definition = jdbcService.findType(type);
        String jdbcUrl = trimToNull(firstNonNull(safeParams, "jdbcUrl", "url"));
        String databaseName = firstNonNull(safeParams, "databaseName", "database", "dbName");
        if (jdbcUrl == null) {
            jdbcUrl = jdbcService.buildJdbcUrl(definition,
                    firstNonNull(safeParams, "dsIp", "host"),
                    firstNonNull(safeParams, "dsPort", "port"),
                    databaseName);
        }
        String driverClass = trimToNull(firstNonNull(safeParams, "driverClass"));
        if (driverClass == null) {
            driverClass = definition != null
                    ? definition.driver() : jdbcService.inferDriver(jdbcUrl);
        }
        if (driverClass == null) {
            throw new BusinessException(400,
                    "无法确定 JDBC 驱动类，请提供 driverClass 或使用内置数据源类型");
        }
        String password = cryptoService.decryptTransport(
                firstNonNull(safeParams, "password"));
        return jdbcService.testConnection(jdbcUrl, driverClass,
                firstNonNull(safeParams, "username", "user"), password);
    }

    /**
     * 按 ID 测试已存数据源（密码从数据库读取并解密）
     */
    private Map<String, Object> testStoredConnection(Long id) {
        DataAccessSource source = requireSource(id);
        String password = cryptoService.decryptStorage(source.getPassword());
        DataSourceTypeDefinition definition = jdbcService.findType(source.getDsType());
        String jdbcUrl = trimToNull(source.getJdbcUrl());
        if (jdbcUrl == null) {
            throw new BusinessException(400, "数据源未配置 jdbcUrl，无法测试连接");
        }
        String driverClass = trimToNull(source.getDriverClass());
        if (driverClass == null) {
            driverClass = definition != null
                    ? definition.driver() : jdbcService.inferDriver(jdbcUrl);
        }
        if (driverClass == null) {
            throw new BusinessException(400,
                    "无法确定 JDBC 驱动类，请补充数据源 driverClass");
        }
        return jdbcService.testConnection(jdbcUrl, driverClass,
                source.getUsername(), password);
    }

    /**
     * 填充派生字段并脱敏：数据库名解析、hasPassword 标记、清空密码
     */
    private void fillDerived(DataAccessSource source) {
        if (source == null) {
            return;
        }
        String databaseName = jdbcService.parseDatabaseName(source.getJdbcUrl());
        if (databaseName != null) {
            source.setDatabaseName(databaseName);
        }
        source.setHasPassword(source.getPassword() != null && !source.getPassword().isEmpty());
        source.setPassword(null);
    }

    /**
     * 查询未删除的数据源，不存在时返回 404
     */
    private DataAccessSource requireSource(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "数据源ID不合法");
        }
        DataAccessSource source = sourceMapper.selectActiveById(id);
        if (source == null) {
            throw new BusinessException(404, "数据源不存在: " + id);
        }
        return source;
    }

    /**
     * 检查存量表是否有 data_layer 列；缺失时写操作忽略该字段，不强制改表
     */
    private boolean supportsDataLayer() {
        Boolean cached = dataLayerColumnPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dataLayerColumnPresent != null) {
                return dataLayerColumnPresent;
            }
            try {
                dataLayerColumnPresent = sourceMapper.countDataLayerColumn() > 0;
            } catch (Exception e) {
                // 无法判断时按存在处理，避免隐藏真实的列/表问题
                log.warn("检查 data_layer 列是否存在失败: {}", e.getMessage());
                dataLayerColumnPresent = Boolean.TRUE;
            }
            return dataLayerColumnPresent;
        }
    }

    /**
     * 生成唯一数据源代码
     */
    private String generateCode(String typeCode) {
        String prefix = typeCode == null ? "DS" : typeCode.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (prefix.isEmpty()) {
            prefix = "DS";
        }
        for (int i = 0; i < 5; i++) {
            String candidate = prefix + "_" + System.currentTimeMillis() + "_"
                    + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (sourceMapper.countByCode(candidate) == 0) {
                return candidate;
            }
        }
        throw new BusinessException(500, "无法生成唯一的数据源代码，请手动指定 dsCode");
    }

    /**
     * 获取当前登录用户；无登录上下文时回退 system
     */
    private static String currentUser() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "system" : String.valueOf(loginId);
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 校验状态仅支持 0/1
     */
    private static void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(400, "状态仅支持 0(禁用)/1(启用)");
        }
    }

    /**
     * 校验所有可选文本字段长度，避免数据库截断/报错
     */
    private static void validateOptionalLengths(DataAccessSource source) {
        requireMaxLen(source.getDsAdapter(), "dsAdapter", 100);
        requireMaxLen(source.getDsIp(), "dsIp", 20);
        requireMaxLen(source.getDsPort(), "dsPort", 10);
        requireMaxLen(source.getUsername(), "username", 100);
        requireMaxLen(source.getConnectionProps(), "connectionProps", 200);
        requireMaxLen(source.getDescription(), "description", 200);
        requireMaxLen(source.getDataLayer(), "dataLayer", 16);
    }

    /**
     * 必填文本校验（trim 后非空）
     */
    private static String requireText(String value, String field, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(400, field + "不能为空");
        }
        requireMaxLen(trimmed, field, maxLength);
        return trimmed;
    }

    /**
     * 可选文本校验：为空时使用默认值
     */
    private static String requireTextWithDefault(String value, String fallback,
                                                 String field, int maxLength) {
        String trimmed = trimToNull(value);
        String result = trimmed == null ? fallback : trimmed;
        return requireText(result, field, maxLength);
    }

    /**
     * 最大长度校验
     */
    private static void requireMaxLen(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400,
                    field + "长度不能超过 " + maxLength + " 个字符");
        }
    }

    /**
     * 从请求体多个候选 key 中取第一个非空字符串
     */
    private static String firstNonNull(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 解析 Long 型 ID
     */
    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "数据源ID格式不正确: " + value);
        }
    }

    /**
     * 忽略大小写比较（空安全）
     */
    private static boolean equalsIgnoreCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    /**
     * 空安全 trim：空白字符串返回 null
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
