package com.dam.processing.develop.service;

import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.security.CredentialCipher;
import com.dam.processing.develop.dto.DevTaskExecutionResult;
import com.dam.processing.develop.dto.DevTaskSchemaResult;
import com.dam.processing.develop.entity.DevTask;
import com.dam.processing.develop.repository.DevTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据开发 SQL 任务执行服务（基础执行入口）
 * <p>
 * 能力边界：只执行 {@code taskType=SQL} 且 {@code status=PUBLISHED} 的任务，只允许
 * 单条只读 {@code SELECT}/{@code WITH} 查询，不实现 Python/Shell 执行。执行使用任务
 * 自身 {@code datasourceConfig} 中的 PostgreSQL JDBC 连接信息，连接强制只读事务，
 * 并设置查询超时与最大返回行数。
 * <p>
 * 安全限制：
 * <ul>
 *   <li>仅接受 {@code jdbc:postgresql://} URL，禁止在 URL 中携带用户名/密码，查询参数
 *       使用白名单（ssl/sslmode/currentSchema/ApplicationName 等）；</li>
 *   <li>SQL 禁止分号（单条语句）、注释、DDL/DML 关键字与 {@code ?} 字面量；</li>
 *   <li>命名参数 {@code :name} 按出现顺序绑定为 {@code ?}，仅支持字符串/数字/布尔/null；</li>
 *   <li>连接设置 {@code readOnly=true} 并执行 {@code SET TRANSACTION READ ONLY}，
 *       配合 {@code statement.setQueryTimeout} 与 {@code setMaxRows}；</li>
 *   <li>不写任何执行历史表，结果只在响应中返回；配置密码不进入日志与异常消息。</li>
 * </ul>
 * 残余风险：若配置中的数据源账号权限过高，只读事务仍可能调用部分服务端函数（如
 * {@code pg_read_file}），生产环境应为执行账号授予最小只读权限。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevTaskExecutionService {

    /** PostgreSQL JDBC URL 前缀，仅允许该协议的连接 */
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql://";

    /** 默认查询超时（秒） */
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    /** 查询超时上限（秒），超过按上限截断 */
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 300;

    /** 默认最大返回行数 */
    private static final int DEFAULT_MAX_ROWS = 1000;

    /** 最大返回行数上限，超过按上限截断 */
    private static final int MAX_MAX_ROWS = 5000;

    /** 建连超时（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** socket 超时在查询超时基础上的额外余量（秒） */
    private static final int SOCKET_TIMEOUT_MARGIN_SECONDS = 10;

    /** 单次 fetchSize 上限，避免一次从服务端拉取过多数据 */
    private static final int MAX_FETCH_SIZE = 500;

    /** 异常消息最大长度，避免把整段 SQL 拼进响应 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /** 结构探测使用的外层别名 */
    private static final String SCHEMA_PROBE_ALIAS = "dam_dev_schema_probe";

    /** 允许的 JDBC URL 查询参数（小写比较），用于阻断 socketFactory 等危险参数 */
    private static final Set<String> ALLOWED_URL_PARAMETERS = Set.of(
            "ssl", "sslmode", "currentschema", "applicationname",
            "assumeminserverversion", "gssencmode", "channelbinding");

    /** 仅允许以 SELECT 或 WITH 开头 */
    private static final Pattern SELECT_OR_WITH_PATTERN =
            Pattern.compile("^\\s*(?:select|with)\\b", Pattern.CASE_INSENSITIVE);

    /** 禁止出现在只读查询中的关键字（DDL/DML/事务/会话/存储过程等） */
    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
            "\\b(?:insert|update|delete|merge|into|create|alter|drop|truncate|comment|grant|revoke"
                    + "|copy|reindex|cluster|vacuum|analyze|refresh|security|load|call|do|set|reset"
                    + "|show|explain|begin|start|commit|rollback|savepoint|release|lock|prepare"
                    + "|execute|deallocate|declare|fetch|move|listen|notify|unlisten|discard"
                    + "|checkpoint)\\b",
            Pattern.CASE_INSENSITIVE);

    /** PostgreSQL 位置参数（$1/$2），本服务只支持命名参数 */
    private static final Pattern POSITIONAL_PARAMETER_PATTERN = Pattern.compile("\\$\\d");

    /** 解析 datasourceConfig JSON 的 Jackson 3 映射器 */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** 任务 Mapper */
    private final DevTaskMapper taskMapper;

    /** 凭据加解密组件：读取 datasourceConfig.password 时解密 */
    private final CredentialCipher credentialCipher;

    /**
     * 执行已发布的 SQL 任务（只读单条查询）
     *
     * @param taskId 任务 ID
     * @param params 命名参数，key 与 SQL 中的 {@code :name} 一致
     * @return 列信息、行数据、行数与耗时
     */
    public DevTaskExecutionResult execute(Long taskId, Map<String, Object> params) {
        DevTask task = requireExecutableSqlTask(taskId);
        JdbcConfig config = parseConfig(task.getDatasourceConfig());
        PreparedSql prepared = prepareSql(task.getContent());

        Map<String, Object> parameterValues = params == null ? Map.of() : params;
        validateParameters(prepared.parameterNames(), parameterValues);

        long startedAt = System.nanoTime();
        try (Connection connection = openReadOnlyConnection(config);
             PreparedStatement statement = connection.prepareStatement(
                     prepared.jdbcSql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            bindParameters(statement, prepared.parameterNames(), parameterValues);
            // 服务端查询超时 + JDBC 最大行数双重限制
            statement.setQueryTimeout(config.queryTimeoutSeconds());
            statement.setMaxRows(config.maxRows());
            statement.setFetchSize(Math.min(config.maxRows(), MAX_FETCH_SIZE));

            List<ColumnInfo> columns;
            List<Map<String, Object>> rows;
            try (ResultSet resultSet = statement.executeQuery()) {
                columns = readColumns(resultSet.getMetaData());
                rows = readRows(resultSet, columns, config.maxRows());
            }
            quietRollback(connection);

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("数据开发 SQL 任务执行成功: taskId={}, rowCount={}, elapsedMs={}, maxRows={}, timeoutSeconds={}",
                    taskId, rows.size(), elapsedMs, config.maxRows(), config.queryTimeoutSeconds());
            return new DevTaskExecutionResult(toColumnMaps(columns), rows, rows.size(), elapsedMs);
        } catch (SQLException e) {
            throw executionFailure(taskId, e, config);
        }
    }

    /**
     * 探测 SQL 任务的命名参数与结果列信息
     * <p>
     * 参数名从 SQL 中提取；列信息通过 {@code SELECT * FROM (<sql>) ... LIMIT 0} 探测，
     * 占位符在该探测语句中替换为 {@code NULL}，不读取业务数据。
     *
     * @param taskId 任务 ID
     * @return 参数名列表与列信息
     */
    public DevTaskSchemaResult describeSchema(Long taskId) {
        DevTask task = requireExecutableSqlTask(taskId);
        JdbcConfig config = parseConfig(task.getDatasourceConfig());
        PreparedSql prepared = prepareSql(task.getContent());

        List<String> parameters = distinct(prepared.parameterNames());
        String probeSql = "SELECT * FROM (" + prepared.probeSql() + ") AS " + SCHEMA_PROBE_ALIAS + " LIMIT 0";

        try (Connection connection = openReadOnlyConnection(config);
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(config.queryTimeoutSeconds());
            statement.setMaxRows(1);
            List<ColumnInfo> columns;
            try (ResultSet resultSet = statement.executeQuery(probeSql)) {
                columns = readColumns(resultSet.getMetaData());
            }
            quietRollback(connection);

            log.info("数据开发 SQL 任务结构探测成功: taskId={}, paramCount={}, columnCount={}",
                    taskId, parameters.size(), columns.size());
            return new DevTaskSchemaResult(parameters, toColumnMaps(columns));
        } catch (SQLException e) {
            throw executionFailure(taskId, e, config);
        }
    }

    /**
     * 加载并校验可执行任务：仅 SQL 类型且已发布
     *
     * @param taskId 任务 ID
     * @return 任务定义
     */
    private DevTask requireExecutableSqlTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        DevTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "数据开发任务不存在: " + taskId);
        }
        if (!DevTask.TYPE_SQL.equals(task.getTaskType())) {
            throw new BusinessException(400, "仅 SQL 类型任务支持执行，当前任务类型: " + task.getTaskType());
        }
        if (!DevTask.STATUS_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "仅 PUBLISHED 状态的任务可以执行，当前状态: " + task.getStatus());
        }
        return task;
    }

    /**
     * 解析并校验任务的数据源配置（不记录任何配置内容）
     * <p>
     * 读取时对根字段 {@code password} 解密：{@code ENC1:} 密文还原为明文，历史明文
     * 原样使用；解密失败时按明文兼容，避免历史配置无法执行。
     *
     * @param rawConfig 任务 datasourceConfig（JSON 字符串）
     * @return JDBC 连接配置
     */
    private JdbcConfig parseConfig(String rawConfig) {
        if (rawConfig == null || rawConfig.isBlank()) {
            throw new BusinessException(400, "任务未配置 datasourceConfig，无法执行 SQL");
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(rawConfig);
        } catch (Exception e) {
            throw new BusinessException(400, "datasourceConfig 不是合法的 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(400, "datasourceConfig 必须是 JSON 对象");
        }

        String url = requireConfigText(root, "url");
        validatePostgresUrl(url);
        String username = requireConfigText(root, "username");
        // 落库值为 ENC1: 密文；历史明文与空值由 CredentialCipher 原样返回
        String password = credentialCipher.decrypt(optionalConfigText(root, "password"));
        int queryTimeoutSeconds = readPositiveInt(root, "queryTimeoutSeconds",
                DEFAULT_QUERY_TIMEOUT_SECONDS, MAX_QUERY_TIMEOUT_SECONDS);
        int maxRows = readPositiveInt(root, "maxRows", DEFAULT_MAX_ROWS, MAX_MAX_ROWS);

        return new JdbcConfig(url, username, password == null ? "" : password,
                queryTimeoutSeconds, maxRows);
    }

    /**
     * 校验 PostgreSQL JDBC URL：协议、主机、禁止 URL 内凭据、查询参数白名单
     *
     * @param url JDBC URL
     */
    private static void validatePostgresUrl(String url) {
        if (!url.toLowerCase(Locale.ROOT).startsWith(POSTGRES_URL_PREFIX)) {
            throw new BusinessException(400, "仅支持 PostgreSQL JDBC URL（jdbc:postgresql://...）");
        }
        URI uri;
        try {
            uri = new URI(url.substring("jdbc:".length()));
        } catch (URISyntaxException e) {
            throw new BusinessException(400, "datasourceConfig.url 不是合法的 JDBC URL");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(400, "datasourceConfig.url 缺少主机名");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException(400, "禁止在 URL 中携带用户名/密码，请使用 username/password 字段");
        }
        if (uri.getFragment() != null) {
            throw new BusinessException(400, "datasourceConfig.url 不允许包含 # 片段");
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawKey = separator < 0 ? pair : pair.substring(0, separator);
            String key;
            try {
                key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "datasourceConfig.url 查询参数编码不合法");
            }
            if (!ALLOWED_URL_PARAMETERS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(400, "datasourceConfig.url 不允许查询参数: " + key
                        + "（仅允许 ssl/sslmode/currentSchema/ApplicationName 等连接参数）");
            }
        }
    }

    /**
     * 读取必填配置文本
     *
     * @param root  配置根节点
     * @param field 字段名
     * @return 去空格后的文本
     */
    private static String requireConfigText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw new BusinessException(400, "datasourceConfig." + field + " 不能为空");
        }
        if (!node.isString()) {
            throw new BusinessException(400, "datasourceConfig." + field + " 必须是字符串");
        }
        String value = node.asString().trim();
        if (value.isEmpty()) {
            throw new BusinessException(400, "datasourceConfig." + field + " 不能为空");
        }
        return value;
    }

    /**
     * 读取可选配置文本（缺省或 null 返回 null）
     *
     * @param root  配置根节点
     * @param field 字段名
     * @return 文本或 null
     */
    private static String optionalConfigText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new BusinessException(400, "datasourceConfig." + field + " 必须是字符串");
        }
        return node.asString();
    }

    /**
     * 读取正整数配置，超过上限时按上限截断
     *
     * @param root         配置根节点
     * @param field        字段名
     * @param defaultValue 缺省值
     * @param maxValue     上限
     * @return 生效值
     */
    private static int readPositiveInt(JsonNode root, String field, int defaultValue, int maxValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isIntegralNumber()) {
            throw new BusinessException(400, "datasourceConfig." + field + " 必须是整数");
        }
        long value = node.asLong();
        if (value < 1) {
            throw new BusinessException(400, "datasourceConfig." + field + " 必须大于 0");
        }
        return (int) Math.min(value, maxValue);
    }

    /**
     * 校验 SQL 并抽取命名参数
     *
     * @param content 任务脚本内容
     * @return 校验后的 SQL 与参数名
     */
    private static PreparedSql prepareSql(String content) {
        String sql = content == null ? "" : content.trim();
        if (sql.isEmpty()) {
            throw new BusinessException(400, "任务 content 为空，无法执行");
        }
        if (sql.indexOf(';') >= 0) {
            throw new BusinessException(400, "仅允许单条 SELECT/WITH 查询，禁止分号");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new BusinessException(400, "SQL 中禁止使用注释");
        }

        // 先屏蔽字符串字面量、双引号标识符与美元引用，再做关键字/参数扫描，避免误判
        String masked = maskLiteralsAndQuotedIdentifiers(sql);
        if (!SELECT_OR_WITH_PATTERN.matcher(masked).find()) {
            throw new BusinessException(400, "仅允许执行 SELECT/WITH 查询");
        }
        Matcher forbidden = FORBIDDEN_KEYWORD_PATTERN.matcher(masked);
        if (forbidden.find()) {
            throw new BusinessException(400, "SQL 中包含不允许的关键字: "
                    + forbidden.group().toUpperCase(Locale.ROOT)
                    + "（仅允许只读查询；如为列名请使用双引号包裹）");
        }
        if (masked.indexOf('?') >= 0) {
            throw new BusinessException(400, "SQL 中不允许出现 ? 字符，请使用 :name 命名参数");
        }
        if (POSITIONAL_PARAMETER_PATTERN.matcher(masked).find()) {
            throw new BusinessException(400, "不支持 $1 形式的位置参数，请使用 :name 命名参数");
        }
        return substituteNamedParameters(sql, masked);
    }

    /**
     * 将命名参数替换为 JDBC 占位符，并生成结构探测用 SQL（占位符替换为 NULL）
     *
     * @param sql    原始 SQL
     * @param masked 屏蔽字面量后的 SQL（长度与原文一致）
     * @return 替换结果
     */
    private static PreparedSql substituteNamedParameters(String sql, String masked) {
        StringBuilder jdbcSql = new StringBuilder(sql.length() + 16);
        StringBuilder probeSql = new StringBuilder(sql.length() + 16);
        List<String> names = new ArrayList<>();
        int index = 0;
        while (index < sql.length()) {
            char current = masked.charAt(index);
            // 跳过 PostgreSQL 的 :: 类型转换；仅识别 :name 形式
            if (current == ':' && (index == 0 || masked.charAt(index - 1) != ':')
                    && index + 1 < sql.length() && isParameterStart(masked.charAt(index + 1))) {
                int end = index + 2;
                while (end < sql.length() && isParameterPart(masked.charAt(end))) {
                    end++;
                }
                names.add(sql.substring(index + 1, end));
                jdbcSql.append('?');
                probeSql.append("NULL");
                index = end;
            } else {
                jdbcSql.append(sql.charAt(index));
                probeSql.append(sql.charAt(index));
                index++;
            }
        }
        return new PreparedSql(jdbcSql.toString(), probeSql.toString(), List.copyOf(names));
    }

    /**
     * 屏蔽字符串字面量、双引号标识符与美元引用（替换为等长空格，保留字符位置）
     *
     * @param sql 原始 SQL
     * @return 屏蔽后的 SQL
     */
    private static String maskLiteralsAndQuotedIdentifiers(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'') {
                index = maskQuoted(sql, index, '\'', isEscapeStringPrefix(sql, index), masked);
            } else if (current == '"') {
                index = maskQuoted(sql, index, '"', false, masked);
            } else if (current == '$') {
                int tagEnd = findDollarQuoteTagEnd(sql, index);
                if (tagEnd > 0) {
                    String tag = sql.substring(index, tagEnd);
                    int closing = sql.indexOf(tag, tagEnd);
                    int end = closing < 0 ? sql.length() : closing + tag.length();
                    appendSpaces(masked, end - index);
                    index = end;
                } else {
                    masked.append(current);
                    index++;
                }
            } else {
                masked.append(current);
                index++;
            }
        }
        return masked.toString();
    }

    /**
     * 屏蔽一段引号包裹的内容
     *
     * @param sql          原始 SQL
     * @param start        起始引号下标
     * @param quote        引号字符
     * @param escapeString 是否为 E'' 转义字符串
     * @param masked       输出缓冲
     * @return 结束下标（指向闭合引号之后）
     */
    private static int maskQuoted(String sql, int start, char quote, boolean escapeString,
                                  StringBuilder masked) {
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (escapeString && current == '\\') {
                // E'' 字符串中反斜杠转义下一个字符
                index += 2;
                continue;
            }
            if (current == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                    // 连续两个引号表示转义，继续处于字符串内部
                    index += 2;
                    continue;
                }
                index++;
                break;
            }
            index++;
        }
        int end = Math.min(index, sql.length());
        appendSpaces(masked, end - start);
        return end;
    }

    /**
     * 判断单引号前是否为 E/e 前缀（E'...' 转义字符串）
     *
     * @param sql   原始 SQL
     * @param index 单引号下标
     * @return 是否为 E'' 字符串
     */
    private static boolean isEscapeStringPrefix(String sql, int index) {
        if (index == 0) {
            return false;
        }
        char prefix = sql.charAt(index - 1);
        if (prefix != 'E' && prefix != 'e') {
            return false;
        }
        return index < 2 || !isIdentifierPart(sql.charAt(index - 2));
    }

    /**
     * 识别美元引用起始标记（$$ 或 $tag$）
     *
     * @param sql   原始 SQL
     * @param start 起始 $ 下标
     * @return 标记结束下标（不含）；不是美元引用时返回 -1
     */
    private static int findDollarQuoteTagEnd(String sql, int start) {
        int index = start + 1;
        if (index >= sql.length()) {
            return -1;
        }
        if (sql.charAt(index) == '$') {
            return index + 1;
        }
        if (!isTagStart(sql.charAt(index))) {
            return -1;
        }
        while (index < sql.length() && isTagPart(sql.charAt(index))) {
            index++;
        }
        if (index < sql.length() && sql.charAt(index) == '$') {
            return index + 1;
        }
        return -1;
    }

    /**
     * 向缓冲追加指定数量的空格
     *
     * @param masked 输出缓冲
     * @param count  空格数量
     */
    private static void appendSpaces(StringBuilder masked, int count) {
        for (int i = 0; i < count; i++) {
            masked.append(' ');
        }
    }

    /**
     * 参数名首字符：字母或下划线
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isParameterStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * 参数名后续字符：字母、数字或下划线
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isParameterPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 美元引用标签首字符：字母或下划线
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isTagStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * 美元引用标签字符
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isTagPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 判断字符是否可作为标识符的一部分
     *
     * @param c 字符
     * @return 是否可作为标识符字符
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 校验命名参数是否齐全且为受支持的基础类型
     *
     * @param parameterNames SQL 中出现的参数名（按出现顺序，可重复）
     * @param params         请求参数
     */
    private static void validateParameters(List<String> parameterNames, Map<String, Object> params) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (String name : parameterNames) {
            if (!params.containsKey(name)) {
                missing.add(name);
                continue;
            }
            Object value = params.get(name);
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
                throw new BusinessException(400, "参数 " + name + " 不支持 NaN/Infinity");
            }
            if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new BusinessException(400, "参数 " + name + " 不支持 NaN/Infinity");
            }
            if (value != null && !(value instanceof String) && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new BusinessException(400, "参数 " + name + " 仅支持字符串/数字/布尔/null");
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(400, "缺少参数: " + String.join(", ", missing));
        }
    }

    /**
     * 绑定命名参数：字符串/null 以未指定类型发送，由 PostgreSQL 按上下文推断
     *
     * @param statement      预编译语句
     * @param parameterNames SQL 中的参数名（按出现顺序，可重复）
     * @param params         请求参数
     * @throws SQLException 绑定失败
     */
    private static void bindParameters(PreparedStatement statement, List<String> parameterNames,
                                       Map<String, Object> params) throws SQLException {
        for (int i = 0; i < parameterNames.size(); i++) {
            Object value = params.get(parameterNames.get(i));
            int index = i + 1;
            if (value == null) {
                // Types.OTHER 在 PostgreSQL 驱动中对应未指定类型，交由服务端推断
                statement.setNull(index, Types.OTHER);
            } else if (value instanceof Number number) {
                statement.setObject(index, normalizeNumber(number));
            } else if (value instanceof Boolean) {
                statement.setObject(index, value);
            } else {
                statement.setObject(index, value, Types.OTHER);
            }
        }
    }

    /**
     * 规整数字类型：BigInteger 转 BigDecimal，避免驱动按未知类型处理
     *
     * @param number 数字
     * @return 可安全绑定的数字
     */
    private static Number normalizeNumber(Number number) {
        if (number instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        return number;
    }

    /**
     * 打开只读 PostgreSQL 连接
     *
     * @param config JDBC 配置
     * @return 已开启只读事务的连接
     * @throws SQLException 建连失败
     */
    private static Connection openReadOnlyConnection(JdbcConfig config) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.username());
        properties.setProperty("password", config.password());
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        properties.setProperty("socketTimeout", String.valueOf(
                (config.queryTimeoutSeconds() + SOCKET_TIMEOUT_MARGIN_SECONDS) * 1000L));
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("ApplicationName", "processing-develop");

        Connection connection = DriverManager.getConnection(config.url(), properties);
        try {
            requirePostgres(connection);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(Math.min(config.queryTimeoutSeconds(), 10));
                // 显式只读事务：即使 close 前忘记 rollback，服务端也会拒绝写操作
                statement.execute("SET TRANSACTION READ ONLY");
            }
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    /**
     * 确认连接目标是 PostgreSQL
     *
     * @param connection 连接
     * @throws SQLException 非 PostgreSQL 时抛出
     */
    private static void requirePostgres(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgresql")) {
            throw new SQLException("目标数据库不是 PostgreSQL，已拒绝执行");
        }
    }

    /**
     * 回滚只读事务；失败仅记 debug（连接即将关闭，不影响查询结果）
     *
     * @param connection 连接
     */
    private static void quietRollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.debug("回滚只读事务失败（连接即将关闭）: {}", e.getMessage());
        }
    }

    /**
     * 读取结果集列信息，重复列名生成唯一键
     *
     * @param metaData 结果集元数据
     * @return 列信息
     * @throws SQLException 读取元数据失败
     */
    private static List<ColumnInfo> readColumns(ResultSetMetaData metaData) throws SQLException {
        int count = metaData.getColumnCount();
        List<ColumnInfo> columns = new ArrayList<>(count);
        Set<String> usedKeys = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            String name = metaData.getColumnLabel(i);
            if (name == null || name.isBlank()) {
                name = metaData.getColumnName(i);
            }
            if (name == null || name.isBlank()) {
                name = "column_" + i;
            }
            String key = name;
            int suffix = 2;
            while (!usedKeys.add(key)) {
                key = name + "_" + suffix;
                suffix++;
            }
            columns.add(new ColumnInfo(key, name, metaData.getColumnTypeName(i)));
        }
        return columns;
    }

    /**
     * 读取行数据，最多读取 maxRows 行
     *
     * @param resultSet 结果集
     * @param columns   列信息
     * @param maxRows   最大行数
     * @return 行数据
     * @throws SQLException 读取失败
     */
    private static List<Map<String, Object>> readRows(ResultSet resultSet, List<ColumnInfo> columns,
                                                      int maxRows) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rows.size() < maxRows && resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i).key(), toJsonValue(resultSet.getObject(i + 1)));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将 JDBC 值转换为可安全序列化的 JSON 值
     *
     * @param value JDBC 原始值
     * @return JSON 友好值
     */
    private static Object toJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte
                || value instanceof BigDecimal || value instanceof BigInteger) {
            return value;
        }
        if (value instanceof Double doubleValue) {
            return Double.isFinite(doubleValue) ? doubleValue : doubleValue.toString();
        }
        if (value instanceof Float floatValue) {
            return Float.isFinite(floatValue) ? floatValue : floatValue.toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDateTime || value instanceof LocalDate || value instanceof LocalTime
                || value instanceof OffsetDateTime || value instanceof OffsetTime
                || value instanceof ZonedDateTime) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Array sqlArray) {
            try {
                return toJsonValue(sqlArray.getArray());
            } catch (SQLException e) {
                return String.valueOf(value);
            }
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(toJsonValue(java.lang.reflect.Array.get(value, i)));
            }
            return items;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> items = new ArrayList<>(collection.size());
            for (Object item : collection) {
                items.add(toJsonValue(item));
            }
            return items;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), toJsonValue(item)));
            return converted;
        }
        // PGobject（jsonb/几何类型等）与其他类型统一按文本返回
        return String.valueOf(value);
    }

    /**
     * 列信息转换为响应结构
     *
     * @param columns 列信息
     * @return 响应列描述
     */
    private static List<Map<String, Object>> toColumnMaps(List<ColumnInfo> columns) {
        List<Map<String, Object>> result = new ArrayList<>(columns.size());
        for (ColumnInfo column : columns) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", column.name());
            item.put("type", column.type());
            result.add(item);
        }
        return result;
    }

    /**
     * 参数名按首次出现顺序去重
     *
     * @param names 参数名列表
     * @return 去重后的参数名
     */
    private static List<String> distinct(List<String> names) {
        return List.copyOf(new LinkedHashSet<>(names));
    }

    /**
     * 构造执行失败异常，日志与消息中的密码一律脱敏
     *
     * @param taskId 任务 ID
     * @param cause  JDBC 异常
     * @param config 连接配置
     * @return 业务异常
     */
    private static BusinessException executionFailure(Long taskId, SQLException cause, JdbcConfig config) {
        String message = sanitizeErrorMessage(cause, config);
        log.warn("数据开发 SQL 任务执行失败: taskId={}, sqlState={}, message={}",
                taskId, cause.getSQLState(), message);
        return new BusinessException(500, "SQL 执行失败: " + message);
    }

    /**
     * 异常消息脱敏：移除配置密码、压缩空白并限制长度
     *
     * @param cause  JDBC 异常
     * @param config 连接配置
     * @return 可返回给前端/日志的消息
     */
    private static String sanitizeErrorMessage(SQLException cause, JdbcConfig config) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        if (config != null && config.password() != null && !config.password().isEmpty()) {
            message = message.replace(config.password(), "***");
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return message;
    }

    /**
     * 解析后的 SQL：JDBC 占位符版本、结构探测版本与参数名顺序
     *
     * @param jdbcSql        {@code :name} 替换为 {@code ?} 的 SQL
     * @param probeSql       {@code :name} 替换为 {@code NULL} 的 SQL
     * @param parameterNames 参数名（按出现顺序，可重复）
     */
    private record PreparedSql(String jdbcSql, String probeSql, List<String> parameterNames) {
    }

    /**
     * 结果列信息
     *
     * @param key  行数据使用的唯一键
     * @param name 列标签
     * @param type 数据库类型名
     */
    private record ColumnInfo(String key, String name, String type) {
    }

    /**
     * 数据源 JDBC 配置
     * <p>
     * toString 固定隐藏密码，避免配置对象被日志或异常拼接时泄露凭据。
     *
     * @param url                  PostgreSQL JDBC URL
     * @param username             用户名
     * @param password             密码
     * @param queryTimeoutSeconds  查询超时（秒）
     * @param maxRows              最大返回行数
     */
    private record JdbcConfig(String url, String username, String password,
                              int queryTimeoutSeconds, int maxRows) {

        /**
         * 隐藏密码的字符串表示
         *
         * @return 脱敏描述
         */
        @Override
        public String toString() {
            return "JdbcConfig[url=" + url + ", username=" + username
                    + ", password=***, queryTimeoutSeconds=" + queryTimeoutSeconds
                    + ", maxRows=" + maxRows + "]";
        }
    }
}
