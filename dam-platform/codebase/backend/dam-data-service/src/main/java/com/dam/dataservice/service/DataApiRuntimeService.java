package com.dam.dataservice.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dam.common.exception.BusinessException;
import com.dam.dataservice.dto.DataApiColumn;
import com.dam.dataservice.dto.DataApiExecutionResult;
import com.dam.dataservice.dto.DataApiSchemaResult;
import com.dam.dataservice.entity.DataApi;
import com.dam.dataservice.repository.DataApiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 数据 API JDBC 运行服务（基础版）
 * <p>
 * 仅支持 {@code status=PUBLISHED} 且 {@code upstreamType=JDBC} 的数据 API，
 * 从 {@code upstream_config}（JSONB）读取 PostgreSQL 连接信息与查询语句，以
 * PreparedStatement 绑定命名参数执行单条 SELECT/WITH 查询。
 * <p>
 * 安全限制：
 * <ul>
 *   <li>只接受 {@code jdbc:postgresql://} 前缀的 URL；</li>
 *   <li>只允许单条 SELECT/WITH，禁止分号、SQL 注释与 DDL/DML 关键字；</li>
 *   <li>连接声明为只读（readOnly + readOnlyMode=always）并关闭自动提交，
 *       查询在只读事务中执行；</li>
 *   <li>设置连接超时、查询超时与最大返回行数 1000；</li>
 *   <li>命名参数按出现顺序替换为 {@code ?} 后绑定，不做 SQL 字符串拼接；</li>
 *   <li>异常消息脱敏（移除密码/URL）并截断，响应不包含连接配置。</li>
 * </ul>
 * 本服务为运行入口的基础实现，不承担完整网关职责（路由、限流、审计等）。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataApiRuntimeService {

    /** 最大返回行数，避免大结果集拖垮服务 */
    private static final int MAX_ROWS = 1000;

    /** 默认连接超时（秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;

    /** 连接超时上限（秒） */
    private static final int MAX_CONNECT_TIMEOUT_SECONDS = 60;

    /** 默认查询超时（秒） */
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    /** 查询超时上限（秒） */
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 300;

    /** 查询语句最大长度，避免超长 SQL 占用资源 */
    private static final int MAX_SQL_LENGTH = 20000;

    /** 错误消息最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    /** 唯一支持的 JDBC URL 前缀 */
    private static final String POSTGRESQL_URL_PREFIX = "jdbc:postgresql://";

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** JDBC 上游类型 */
    private static final String UPSTREAM_TYPE_JDBC = "JDBC";

    /** 允许的查询起始关键字 */
    private static final Set<String> ALLOWED_QUERY_KEYWORDS = Set.of("SELECT", "WITH");

    /**
     * 禁止出现在查询中的关键字（不区分大小写）
     * <p>
     * 覆盖 DML、DDL 以及可能产生副作用的管理/过程调用语句。字符串字面量与
     * 双引号标识符内的内容不参与关键字扫描。
     */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "COPY", "INTO", "RETURNING",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "COMMENT", "GRANT", "REVOKE", "RENAME",
            "CALL", "EXECUTE", "DO", "VACUUM", "ANALYZE", "REINDEX", "CLUSTER", "REFRESH",
            "SET", "RESET", "DISCARD", "LOCK", "LISTEN", "NOTIFY", "UNLISTEN",
            "PREPARE", "DEALLOCATE", "DECLARE");

    /** 异常消息中的 password=xxx 片段，用于脱敏 */
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile(
            "(?i)password\\s*=\\s*[^\\s;&]+");

    /** 数据 API Mapper */
    private final DataApiMapper dataApiMapper;

    /**
     * 执行已发布的数据 API
     *
     * @param id     API ID
     * @param params 命名参数，可为空
     * @return 列信息、数据行、行数与耗时
     */
    public DataApiExecutionResult execute(Long id, Map<String, Object> params) {
        RuntimeTarget target = resolveTarget(id);
        Map<String, Object> values = params == null ? Collections.emptyMap() : params;
        long startedAt = System.nanoTime();
        try (Connection connection = openConnection(target)) {
            beginReadOnlyTransaction(connection);
            try (PreparedStatement statement = prepare(connection, target.parsedQuery().sql(),
                    target.queryTimeoutSeconds(), MAX_ROWS)) {
                bindParameters(statement, target.parsedQuery().paramNames(), values);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<DataApiColumn> columns = readColumns(resultSet.getMetaData());
                    List<String> keys = uniqueKeys(columns);
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rows.size() < MAX_ROWS && resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columns.size(); i++) {
                            row.put(keys.get(i - 1), normalizeValue(resultSet.getObject(i)));
                        }
                        rows.add(row);
                    }
                    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    return new DataApiExecutionResult(columns, rows, rows.size(), elapsedMs);
                }
            } finally {
                rollbackQuietly(connection);
            }
        } catch (SQLException e) {
            String detail = sanitizeMessage(e, target);
            log.warn("数据 API 执行失败: apiId={}, error={}", id, detail);
            throw new BusinessException(502, "上游 JDBC 执行失败：" + detail);
        }
    }

    /**
     * 探测数据 API 查询的参数与返回列结构
     * <p>
     * 通过外层包装 {@code SELECT * FROM (<query>) alias LIMIT 0} 获取 ResultSet
     * 元数据，不读取真实数据；参数统一绑定为 NULL。仅用于结构探测，不返回数据行。
     *
     * @param id API ID
     * @return 参数名列表与列信息
     */
    public DataApiSchemaResult schema(Long id) {
        RuntimeTarget target = resolveTarget(id);
        // 外层 LIMIT 0 保证不产生真实数据行；查询本身仍是单条 SELECT/WITH
        String probeSql = "SELECT * FROM (" + target.parsedQuery().sql()
                + ") AS dam_api_schema_probe LIMIT 0";
        try (Connection connection = openConnection(target)) {
            beginReadOnlyTransaction(connection);
            try (PreparedStatement statement = prepare(connection, probeSql,
                    target.queryTimeoutSeconds(), 1)) {
                List<String> paramNames = target.parsedQuery().paramNames();
                // LIMIT 0 不会读取数据，参数以 NULL（未知类型）绑定即可完成语句描述
                for (int i = 1; i <= paramNames.size(); i++) {
                    statement.setNull(i, Types.NULL);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<DataApiColumn> columns = readColumns(resultSet.getMetaData());
                    return new DataApiSchemaResult(List.copyOf(paramNames), columns);
                }
            } finally {
                rollbackQuietly(connection);
            }
        } catch (SQLException e) {
            String detail = sanitizeMessage(e, target);
            log.warn("数据 API Schema 探测失败: apiId={}, error={}", id, detail);
            throw new BusinessException(502, "无法解析查询列信息：" + detail);
        }
    }

    /**
     * 校验 API 状态、上游类型与上游配置，解析运行目标
     *
     * @param id API ID
     * @return 运行目标（含连接信息与解析后的查询）
     */
    private RuntimeTarget resolveTarget(Long id) {
        if (id == null) {
            throw new BusinessException(400, "API ID 不能为空");
        }
        DataApi api = dataApiMapper.selectById(id);
        if (api == null) {
            throw new BusinessException(404, "数据 API 不存在");
        }
        String status = trim(api.getStatus());
        if (status == null || !STATUS_PUBLISHED.equalsIgnoreCase(status)) {
            throw new BusinessException(409, "仅 PUBLISHED 状态的数据 API 可以执行");
        }
        String upstreamType = trim(api.getUpstreamType());
        if (upstreamType == null || !UPSTREAM_TYPE_JDBC.equalsIgnoreCase(upstreamType)) {
            throw new BusinessException(400, "仅支持 upstreamType=JDBC 的数据 API");
        }
        String rawConfig = trim(api.getUpstreamConfig());
        if (rawConfig == null || rawConfig.isEmpty()) {
            throw new BusinessException(400, "upstreamConfig 不能为空");
        }
        JSONObject config;
        try {
            config = JSONUtil.parseObj(rawConfig);
        } catch (RuntimeException e) {
            throw new BusinessException(400, "upstreamConfig 必须是合法的 JSON 对象");
        }

        String url = trim(config.getStr("url"));
        if (url == null || url.isEmpty()) {
            throw new BusinessException(400, "upstreamConfig.url 不能为空");
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith(POSTGRESQL_URL_PREFIX)) {
            throw new BusinessException(400,
                    "仅支持 PostgreSQL JDBC URL（jdbc:postgresql:// 前缀）");
        }
        String query = config.getStr("query");
        ParsedQuery parsedQuery = parseAndValidateQuery(query);
        String username = config.getStr("username");
        String password = config.getStr("password");
        int connectTimeout = resolveTimeout(config, "connectTimeoutSeconds",
                DEFAULT_CONNECT_TIMEOUT_SECONDS, MAX_CONNECT_TIMEOUT_SECONDS);
        int queryTimeout = resolveTimeout(config, "queryTimeoutSeconds",
                DEFAULT_QUERY_TIMEOUT_SECONDS, MAX_QUERY_TIMEOUT_SECONDS);
        return new RuntimeTarget(url, username, password, parsedQuery,
                connectTimeout, queryTimeout);
    }

    /**
     * 建立只读连接
     * <p>
     * 通过连接属性声明只读模式并设置连接超时；密码只作为连接属性使用，
     * 不写入日志或响应。
     *
     * @param target 运行目标
     * @return JDBC 连接
     * @throws SQLException 建连失败
     */
    private Connection openConnection(RuntimeTarget target) throws SQLException {
        Properties properties = new Properties();
        if (hasText(target.username())) {
            properties.setProperty("user", target.username());
        }
        if (target.password() != null && !target.password().isEmpty()) {
            properties.setProperty("password", target.password());
        }
        properties.setProperty("connectTimeout", String.valueOf(target.connectTimeoutSeconds()));
        // readOnly + readOnlyMode=always：连接层面拒绝写操作
        properties.setProperty("readOnly", "true");
        properties.setProperty("readOnlyMode", "always");
        properties.setProperty("ApplicationName", "dam-data-service-runtime");
        return DriverManager.getConnection(target.url(), properties);
    }

    /**
     * 开启只读事务（关闭自动提交并由调用方统一回滚）
     *
     * @param connection JDBC 连接
     * @throws SQLException 设置失败
     */
    private void beginReadOnlyTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
    }

    /**
     * 创建带超时与最大行数限制的 PreparedStatement
     *
     * @param connection    JDBC 连接
     * @param sql           已参数化的 SQL
     * @param timeoutSeconds 查询超时（秒）
     * @param maxRows       最大行数
     * @return PreparedStatement
     * @throws SQLException 创建失败
     */
    private PreparedStatement prepare(Connection connection, String sql,
                                      int timeoutSeconds, int maxRows) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(timeoutSeconds);
        statement.setMaxRows(maxRows);
        // 只读事务内使用游标分批读取，避免驱动一次性缓存全部结果
        statement.setFetchSize(Math.min(maxRows, 200));
        return statement;
    }

    /**
     * 按查询中命名参数的出现顺序绑定值
     *
     * @param statement  PreparedStatement
     * @param paramNames 参数名（可重复）
     * @param params     参数值
     * @throws SQLException 绑定失败
     */
    private void bindParameters(PreparedStatement statement, List<String> paramNames,
                                Map<String, Object> params) throws SQLException {
        for (int i = 0; i < paramNames.size(); i++) {
            String name = paramNames.get(i);
            if (!params.containsKey(name)) {
                throw new BusinessException(400, "缺少查询参数：" + name);
            }
            setValue(statement, i + 1, params.get(name));
        }
    }

    /**
     * 安全绑定单个参数值；Map/Collection 序列化为 JSON 字符串，避免驱动无法处理
     *
     * @param statement PreparedStatement
     * @param index     参数下标（从 1 开始）
     * @param value     参数值
     * @throws SQLException 绑定失败
     */
    private void setValue(PreparedStatement statement, int index, Object value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NULL);
        } else if (value instanceof String text) {
            statement.setString(index, text);
        } else if (value instanceof Boolean bool) {
            statement.setBoolean(index, bool);
        } else if (value instanceof Integer number) {
            statement.setInt(index, number);
        } else if (value instanceof Long number) {
            statement.setLong(index, number);
        } else if (value instanceof Short number) {
            statement.setShort(index, number);
        } else if (value instanceof Byte number) {
            statement.setByte(index, number);
        } else if (value instanceof BigDecimal number) {
            statement.setBigDecimal(index, number);
        } else if (value instanceof Float number) {
            statement.setFloat(index, number);
        } else if (value instanceof Double number) {
            statement.setDouble(index, number);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else if (value instanceof java.util.Date date) {
            statement.setTimestamp(index, new Timestamp(date.getTime()));
        } else if (value instanceof java.time.LocalDate date) {
            statement.setObject(index, date);
        } else if (value instanceof java.time.LocalDateTime dateTime) {
            statement.setObject(index, dateTime);
        } else if (value instanceof java.time.LocalTime time) {
            statement.setObject(index, time);
        } else if (value instanceof java.time.OffsetDateTime dateTime) {
            statement.setObject(index, dateTime);
        } else if (value instanceof java.time.Instant instant) {
            statement.setObject(index, instant);
        } else if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            statement.setString(index, JSONUtil.toJsonStr(value));
        } else if (value instanceof Number number) {
            statement.setObject(index, number);
        } else {
            statement.setString(index, String.valueOf(value));
        }
    }

    /**
     * 校验并解析查询：限制单条 SELECT/WITH，提取命名参数并替换为 {@code ?}
     *
     * @param rawQuery 原始查询
     * @return 解析后的 SQL 与参数名（按出现顺序）
     */
    private ParsedQuery parseAndValidateQuery(String rawQuery) {
        String query = trim(rawQuery);
        if (query == null || query.isEmpty()) {
            throw new BusinessException(400, "upstreamConfig.query 不能为空");
        }
        if (query.length() > MAX_SQL_LENGTH) {
            throw new BusinessException(400,
                    "查询语句长度不能超过 " + MAX_SQL_LENGTH + " 字符");
        }
        if (query.indexOf(';') >= 0) {
            throw new BusinessException(400, "查询只允许单条 SELECT/WITH 语句，禁止分号");
        }
        if (query.contains("--") || query.contains("/*") || query.contains("*/")) {
            throw new BusinessException(400, "查询禁止包含 SQL 注释");
        }
        if (query.indexOf('\0') >= 0) {
            throw new BusinessException(400, "查询包含非法字符");
        }
        String firstKeyword = firstKeyword(query);
        if (!ALLOWED_QUERY_KEYWORDS.contains(firstKeyword)) {
            throw new BusinessException(400, "仅允许单条 SELECT/WITH 查询");
        }

        StringBuilder sql = new StringBuilder(query.length() + 16);
        List<String> paramNames = new ArrayList<>();
        int i = 0;
        int length = query.length();
        while (i < length) {
            char current = query.charAt(i);
            if (current == '\'') {
                int end = skipQuoted(query, i, '\'');
                sql.append(query, i, end);
                i = end;
            } else if (current == '"') {
                int end = skipQuoted(query, i, '"');
                sql.append(query, i, end);
                i = end;
            } else if (current == '$') {
                int end = skipDollarQuoted(query, i);
                if (end > i) {
                    sql.append(query, i, end);
                    i = end;
                } else {
                    sql.append(current);
                    i++;
                }
            } else if (Character.isLetter(current) || current == '_') {
                int start = i;
                while (i < length
                        && (Character.isLetterOrDigit(query.charAt(i))
                        || query.charAt(i) == '_' || query.charAt(i) == '$')) {
                    i++;
                }
                String word = query.substring(start, i);
                if (FORBIDDEN_KEYWORDS.contains(word.toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(400, "查询包含不允许的关键字：" + word);
                }
                sql.append(word);
            } else if (current == ':') {
                if (i + 1 < length && query.charAt(i + 1) == ':') {
                    // PostgreSQL 类型转换 ::type，原样保留
                    sql.append("::");
                    i += 2;
                } else if (i + 1 < length
                        && (Character.isLetter(query.charAt(i + 1))
                        || query.charAt(i + 1) == '_')) {
                    int start = i + 1;
                    int end = start + 1;
                    while (end < length
                            && (Character.isLetterOrDigit(query.charAt(end))
                            || query.charAt(end) == '_')) {
                        end++;
                    }
                    paramNames.add(query.substring(start, end));
                    sql.append('?');
                    i = end;
                } else {
                    sql.append(current);
                    i++;
                }
            } else {
                sql.append(current);
                i++;
            }
        }
        return new ParsedQuery(sql.toString(), List.copyOf(paramNames));
    }

    /**
     * 跳过单引号字符串或双引号标识符（支持双写转义），返回结束位置（不含）
     *
     * @param sql   查询
     * @param start 起始引号位置
     * @param quote 引号字符
     * @return 结束位置
     */
    private int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        // 未闭合的引号交给数据库报错，避免静默改写
        return sql.length();
    }

    /**
     * 跳过 PostgreSQL dollar-quoted 字符串（如 $$...$$、$tag$...$tag$）
     *
     * @param sql   查询
     * @param start dollar 符号位置
     * @return 结束位置；不是 dollar-quoted 字符串时返回 start
     */
    private int skipDollarQuoted(String sql, int start) {
        int length = sql.length();
        int i = start + 1;
        if (i < length && sql.charAt(i) == '$') {
            int end = sql.indexOf("$$", i + 1);
            return end < 0 ? start : end + 2;
        }
        int tagEnd = i;
        while (tagEnd < length
                && (Character.isLetterOrDigit(sql.charAt(tagEnd))
                || sql.charAt(tagEnd) == '_')) {
            tagEnd++;
        }
        if (tagEnd == i || tagEnd >= length || sql.charAt(tagEnd) != '$'
                || (!Character.isLetter(sql.charAt(i)) && sql.charAt(i) != '_')) {
            return start;
        }
        String tag = sql.substring(start, tagEnd + 1);
        int end = sql.indexOf(tag, tagEnd + 1);
        return end < 0 ? start : end + tag.length();
    }

    /**
     * 读取查询起始关键字
     *
     * @param query 查询
     * @return 大写起始关键字（无法识别时返回空串）
     */
    private String firstKeyword(String query) {
        int i = 0;
        while (i < query.length() && Character.isWhitespace(query.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < query.length()
                && (Character.isLetter(query.charAt(i)) || query.charAt(i) == '_')) {
            i++;
        }
        return query.substring(start, i).toUpperCase(Locale.ROOT);
    }

    /**
     * 读取结果集列信息
     *
     * @param metaData 结果集元数据
     * @return 列信息
     * @throws SQLException 读取失败
     */
    private List<DataApiColumn> readColumns(ResultSetMetaData metaData) throws SQLException {
        int count = metaData.getColumnCount();
        List<DataApiColumn> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String name = metaData.getColumnName(i);
            String label = metaData.getColumnLabel(i);
            if (!hasText(label)) {
                label = name;
            }
            columns.add(new DataApiColumn(name, label,
                    metaData.getColumnTypeName(i), metaData.getColumnType(i)));
        }
        return columns;
    }

    /**
     * 生成唯一的结果行 key：重复列标签追加 _2、_3 后缀，避免数据被覆盖
     *
     * @param columns 列信息
     * @return 与列顺序一致的 key 列表
     */
    private List<String> uniqueKeys(List<DataApiColumn> columns) {
        List<String> keys = new ArrayList<>(columns.size());
        Set<String> used = new HashSet<>();
        for (DataApiColumn column : columns) {
            String base = hasText(column.getLabel()) ? column.getLabel() : column.getName();
            if (!hasText(base)) {
                base = "column";
            }
            String key = base;
            int suffix = 2;
            while (!used.add(key)) {
                key = base + "_" + suffix;
                suffix++;
            }
            keys.add(key);
        }
        return keys;
    }

    /**
     * 将 JDBC 值转换为便于 JSON 序列化的值
     *
     * @param value JDBC 原始值
     * @return 规范化后的值
     * @throws SQLException 读取数组失败
     */
    private Object normalizeValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof java.sql.Array array) {
            try {
                Object raw = array.getArray();
                if (raw instanceof Object[] items) {
                    List<Object> list = new ArrayList<>(items.length);
                    for (Object item : items) {
                        list.add(normalizeValue(item));
                    }
                    return list;
                }
                return normalizeValue(raw);
            } finally {
                try {
                    array.free();
                } catch (SQLException ignored) {
                    // 释放失败不影响结果返回
                }
            }
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof java.time.temporal.TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        // PostgreSQL json/jsonb 等 PGobject 直接取其文本值
        if ("org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    /**
     * 解析超时配置，缺省使用默认值，超过上限按上限截断
     *
     * @param config       上游配置
     * @param key          配置键
     * @param defaultValue 默认值（秒）
     * @param maxValue     上限（秒）
     * @return 生效的超时秒数
     */
    private int resolveTimeout(JSONObject config, String key, int defaultValue, int maxValue) {
        Object raw = config.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, key + " 必须是整数（秒）");
        }
        if (value < 1) {
            throw new BusinessException(400, key + " 必须大于 0");
        }
        return Math.min(value, maxValue);
    }

    /**
     * 脱敏并截断 JDBC 异常消息：移除密码、连接 URL 与换行
     *
     * @param e      异常
     * @param target 运行目标
     * @return 可对外返回的错误摘要
     */
    private String sanitizeMessage(SQLException e, RuntimeTarget target) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        if (hasText(target.url())) {
            message = message.replace(target.url(), "<jdbc-url>");
        }
        // 密码可能出现在 URL 参数或异常文本中，先精确替换再兜底 password=xxx
        if (target.password() != null && target.password().length() >= 4) {
            message = message.replace(target.password(), "******");
        }
        message = PASSWORD_ASSIGNMENT.matcher(message).replaceAll("password=******");
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return message;
    }

    /**
     * 回滚只读事务并忽略异常（连接关闭/失败时不覆盖原始异常）
     *
     * @param connection JDBC 连接
     */
    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 只读事务回滚失败不影响结果
        }
    }

    /**
     * 判断文本是否非空
     *
     * @param value 原始值
     * @return 是否包含非空白字符
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    /**
     * 运行目标：连接信息与解析后的查询
     *
     * @param url                   PostgreSQL JDBC URL
     * @param username              用户名
     * @param password              密码（仅用于建连，不对外返回）
     * @param parsedQuery           解析后的查询
     * @param connectTimeoutSeconds 连接超时（秒）
     * @param queryTimeoutSeconds   查询超时（秒）
     */
    private record RuntimeTarget(String url, String username, String password,
                                 ParsedQuery parsedQuery, int connectTimeoutSeconds,
                                 int queryTimeoutSeconds) {
    }

    /**
     * 解析后的查询
     *
     * @param sql        `:name` 已替换为 `?` 的 SQL
     * @param paramNames 参数名（按出现顺序，可重复）
     */
    private record ParsedQuery(String sql, List<String> paramNames) {
    }
}
