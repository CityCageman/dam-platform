package com.dam.platform.scheduler.service;

import com.dam.common.exception.BusinessException;
import com.dam.common.security.CredentialCipher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调度 DAG 定义校验与脱敏工具。
 * <p>
 * 仅支持节点顺序执行的 SQL DAG：
 * <ul>
 *   <li>dagCode/node.code 为 1-64 位字母、数字、下划线或连字符；node.code 唯一；</li>
 *   <li>节点 type 固定为 SQL；datasource.url 仅允许 {@code jdbc:postgresql://}；</li>
 *   <li>SQL 仅允许单条 SELECT/WITH，禁止分号、注释、DML/DDL 与事务/会话命令；</li>
 *   <li>响应脱敏只替换 datasource.password，不影响库内原始定义与执行。</li>
 * </ul>
 * 错误消息不包含密码或完整定义内容。
 *
 * @author DAM Platform
 */
public final class ScheduleDagValidator {

    /** DAG/节点编码最大长度（与 dag_code 一致） */
    public static final int MAX_CODE_LENGTH = 64;

    /** DAG/节点名称最大长度 */
    public static final int MAX_NAME_LENGTH = 200;

    /** SQL 最大长度 */
    public static final int MAX_SQL_LENGTH = 65535;

    /** DAG 最大节点数 */
    public static final int MAX_NODES = 200;

    /** datasource.url 最大长度 */
    private static final int MAX_URL_LENGTH = 2048;

    /** datasource.username 最大长度 */
    private static final int MAX_USERNAME_LENGTH = 256;

    /** datasource.password 最大长度 */
    private static final int MAX_PASSWORD_LENGTH = 4096;

    /**
     * datasource.password 密文最大长度。
     * <p>
     * 明文上限 4096 字符，AES-GCM 密文经 Base64 编码后体积约为明文的 5.4 倍；
     * 读取库内定义时按密文上限放行，避免长密码加密后无法通过再次校验。
     */
    private static final int MAX_ENCRYPTED_PASSWORD_LENGTH = 32768;

    /** 编码格式：字母或数字开头，只允许字母、数字、下划线与连字符 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    /** 仅允许 PostgreSQL JDBC URL */
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql://";

    /** 允许的 JDBC URL 查询参数（小写比较），阻断 socketFactory 等危险参数 */
    private static final Set<String> ALLOWED_URL_PARAMETERS = Set.of(
            "ssl", "sslmode", "currentschema", "applicationname",
            "assumeminserverversion", "gssencmode", "channelbinding");

    /** 仅允许以 SELECT 或 WITH 开头 */
    private static final Pattern SELECT_OR_WITH_PATTERN =
            Pattern.compile("^\\s*(?:select|with)\\b", Pattern.CASE_INSENSITIVE);

    /** 只读查询中禁止出现的关键字（DDL/DML/事务/会话/存储过程等） */
    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
            "\\b(?:insert|update|delete|merge|into|create|alter|drop|truncate|comment|grant|revoke"
                    + "|copy|reindex|cluster|vacuum|analyze|refresh|security|load|call|do|set|reset"
                    + "|show|explain|begin|start|commit|rollback|savepoint|release|lock|prepare"
                    + "|execute|deallocate|declare|fetch|move|listen|notify|unlisten|discard"
                    + "|checkpoint|returning)\\b",
            Pattern.CASE_INSENSITIVE);

    /** JSON 映射器 */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private ScheduleDagValidator() {
    }

    /**
     * 校验并规范化 DAG 编码。
     *
     * @param raw 原始编码
     * @return 裁剪后的编码
     */
    public static String requireDagCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, "DAG 编码不能为空");
        }
        String code = raw.trim();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessException(400, "DAG 编码不能超过 " + MAX_CODE_LENGTH + " 个字符");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(400,
                    "DAG 编码仅允许字母、数字、下划线与连字符，且必须以字母或数字开头");
        }
        return code;
    }

    /**
     * 校验并规范化 DAG 定义。
     * <p>
     * 接受 JSON 对象或 JSON 字符串；返回深拷贝后的对象，可安全序列化后落库。
     *
     * @param raw 请求中的 definition
     * @return 规范化后的 JSON 对象
     */
    public static ObjectNode normalizeDefinition(JsonNode raw) {
        JsonNode parsed = parseDefinitionNode(raw);
        if (!parsed.isObject()) {
            throw new BusinessException(400, "DAG definition 必须是 JSON 对象");
        }
        ObjectNode root = (ObjectNode) parsed.deepCopy();
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new BusinessException(400, "DAG definition.nodes 必须是非空数组");
        }
        if (nodes.size() > MAX_NODES) {
            throw new BusinessException(400, "DAG 节点数不能超过 " + MAX_NODES);
        }

        Set<String> codes = new HashSet<>();
        int index = 0;
        for (JsonNode item : nodes) {
            String label = "nodes[" + index + "]";
            if (!item.isObject()) {
                throw new BusinessException(400, label + " 必须是 JSON 对象");
            }
            ObjectNode node = (ObjectNode) item;
            String code = requireText(node.get("code"), label + ".code", MAX_CODE_LENGTH);
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw new BusinessException(400,
                        label + ".code 仅允许字母、数字、下划线与连字符，且必须以字母或数字开头");
            }
            if (!codes.add(code)) {
                throw new BusinessException(400, "DAG 节点编码重复: " + code);
            }

            String name = requireText(node.get("name"), label + ".name", MAX_NAME_LENGTH);
            String type = requireText(node.get("type"), label + ".type", 16);
            if (!"SQL".equalsIgnoreCase(type)) {
                throw new BusinessException(400, label + ".type 仅支持 SQL");
            }

            JsonNode datasource = node.get("datasource");
            if (datasource == null || !datasource.isObject()) {
                throw new BusinessException(400, label + ".datasource 必须是 JSON 对象");
            }
            ObjectNode datasourceObject = (ObjectNode) datasource;
            String url = requireText(datasourceObject.get("url"),
                    label + ".datasource.url", MAX_URL_LENGTH);
            validatePostgresUrl(url);
            String username = requireText(datasourceObject.get("username"),
                    label + ".datasource.username", MAX_USERNAME_LENGTH);
            String password = optionalPassword(datasourceObject.get("password"), label);

            String sql = requireText(node.get("sql"), label + ".sql", MAX_SQL_LENGTH);
            validateSql(sql, label);

            // 规范化字段，确保落库内容与校验结果一致
            node.put("code", code);
            node.put("name", name);
            node.put("type", "SQL");
            node.put("sql", sql);
            datasourceObject.put("url", url);
            datasourceObject.put("username", username);
            datasourceObject.put("password", password);
            index++;
        }
        return root;
    }

    /**
     * 将定义对象序列化为 jsonb 文本。
     *
     * @param definition 已规范化定义
     * @return JSON 文本
     */
    public static String toJson(JsonNode definition) {
        try {
            return JSON_MAPPER.writeValueAsString(definition);
        } catch (Exception e) {
            throw new BusinessException(400, "DAG definition 序列化失败");
        }
    }

    /**
     * 解析库内 DAG 定义并返回可执行 SQL 节点列表。
     * <p>
     * 库内定义在写入时已校验；执行前再次校验，避免绕过 API 直接改库引入的非法定义。
     * 返回节点顺序与 definition.nodes 一致。
     *
     * @param rawDefinition jsonb 原文字符串
     * @return SQL 节点列表
     */
    public static List<SqlNode> readNodes(String rawDefinition) {
        if (rawDefinition == null || rawDefinition.isBlank()) {
            throw new BusinessException(400, "DAG 定义为空");
        }
        JsonNode raw;
        try {
            raw = JSON_MAPPER.readTree(rawDefinition);
        } catch (Exception e) {
            throw new BusinessException(400, "DAG 定义不是合法 JSON");
        }
        ObjectNode root = normalizeDefinition(raw);
        JsonNode nodes = root.get("nodes");
        List<SqlNode> result = new ArrayList<>(nodes.size());
        for (JsonNode item : nodes) {
            ObjectNode node = (ObjectNode) item;
            ObjectNode datasource = (ObjectNode) node.get("datasource");
            result.add(new SqlNode(
                    node.get("code").asString(),
                    node.get("name").asString(),
                    datasource.get("url").asString(),
                    datasource.get("username").asString(),
                    datasource.get("password").asString(),
                    node.get("sql").asString()));
        }
        return List.copyOf(result);
    }

    /**
     * 返回脱敏后的 DAG 定义：所有节点的 datasource.password 替换为 {@code ***}。
     * <p>
     * 始终返回新的 JSON 树，不修改库内原文。
     *
     * @param rawDefinition jsonb 原文字符串
     * @return 脱敏后的定义
     */
    public static JsonNode maskPasswords(String rawDefinition) {
        JsonNode raw;
        try {
            raw = JSON_MAPPER.readTree(rawDefinition);
        } catch (Exception e) {
            throw new BusinessException(500, "DAG 定义损坏，无法读取");
        }
        if (raw == null || !raw.isObject()) {
            throw new BusinessException(500, "DAG 定义损坏，无法读取");
        }
        JsonNode nodes = raw.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode item : nodes) {
                if (!item.isObject()) {
                    continue;
                }
                JsonNode datasource = item.get("datasource");
                if (datasource instanceof ObjectNode datasourceObject) {
                    datasourceObject.put("password", "***");
                }
            }
        }
        return raw;
    }

    /**
     * 校验 SQL：只允许单条 SELECT/WITH，禁止分号、注释与写/DDL 关键字。
     *
     * @param sql   已裁剪 SQL
     * @param label 节点标签，仅用于错误定位
     */
    private static void validateSql(String sql, String label) {
        if (sql.indexOf(';') >= 0) {
            throw new BusinessException(400,
                    label + ".sql 仅允许单条 SELECT/WITH 查询，禁止分号");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new BusinessException(400, label + ".sql 禁止使用注释");
        }

        // 先屏蔽字符串字面量、双引号标识符与美元引用，再做关键字扫描，避免误判
        String masked = maskLiteralsAndQuotedIdentifiers(sql);
        if (!SELECT_OR_WITH_PATTERN.matcher(masked).find()) {
            throw new BusinessException(400, label + ".sql 仅允许 SELECT/WITH 查询");
        }
        Matcher forbidden = FORBIDDEN_KEYWORD_PATTERN.matcher(masked);
        if (forbidden.find()) {
            throw new BusinessException(400, label + ".sql 包含不允许的关键字: "
                    + forbidden.group().toUpperCase(Locale.ROOT)
                    + "（仅允许只读查询；如为列名请使用双引号包裹）");
        }
    }

    /**
     * 校验 PostgreSQL JDBC URL：协议、主机、禁止 URL 内凭据与查询参数白名单。
     *
     * @param url JDBC URL
     */
    private static void validatePostgresUrl(String url) {
        if (!url.toLowerCase(Locale.ROOT).startsWith(POSTGRES_URL_PREFIX)) {
            throw new BusinessException(400, "datasource.url 仅支持 jdbc:postgresql:// 前缀");
        }
        URI uri;
        try {
            uri = new URI(url.substring("jdbc:".length()));
        } catch (URISyntaxException e) {
            throw new BusinessException(400, "datasource.url 不是合法的 JDBC URL");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(400, "datasource.url 缺少主机名");
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException(400, "禁止在 URL 中携带用户名/密码，请使用 datasource 字段");
        }
        if (uri.getFragment() != null) {
            throw new BusinessException(400, "datasource.url 不允许包含 # 片段");
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
                throw new BusinessException(400, "datasource.url 查询参数编码不合法");
            }
            if (!ALLOWED_URL_PARAMETERS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(400, "datasource.url 不允许查询参数: " + key);
            }
        }
    }

    /**
     * 解析 definition：允许 JSON 对象或 JSON 字符串，其它类型拒绝。
     *
     * @param raw 原始值
     * @return JSON 节点
     */
    private static JsonNode parseDefinitionNode(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            throw new BusinessException(400, "DAG definition 不能为空");
        }
        if (raw.isString()) {
            String text = raw.asString().trim();
            if (text.isEmpty()) {
                throw new BusinessException(400, "DAG definition 不能为空");
            }
            try {
                return JSON_MAPPER.readTree(text);
            } catch (Exception e) {
                throw new BusinessException(400, "DAG definition 不是合法 JSON");
            }
        }
        return raw;
    }

    /**
     * 读取必填文本并校验长度。
     *
     * @param node   字段节点
     * @param field  字段名
     * @param maxLen 最大长度
     * @return 裁剪后的文本
     */
    private static String requireText(JsonNode node, String field, int maxLen) {
        if (node == null || node.isNull() || !node.isString() || node.asString().isBlank()) {
            throw new BusinessException(400, field + " 不能为空");
        }
        String value = node.asString().trim();
        if (value.length() > maxLen) {
            throw new BusinessException(400, field + " 不能超过 " + maxLen + " 个字符");
        }
        return value;
    }

    /**
     * 读取可空密码：缺省/null 归一为空串；不裁剪，避免改变真实密码。
     * <p>
     * 明文仍受 4096 字符限制；已带 {@code ENC1:} 前缀的密文按加密后的上限校验，
     * 使落库密文在读取/执行时能通过同一套校验。
     *
     * @param node  密码节点
     * @param label 节点标签
     * @return 原始密码或空串
     */
    private static String optionalPassword(JsonNode node, String label) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isString()) {
            throw new BusinessException(400, label + ".datasource.password 必须是字符串");
        }
        String password = node.asString();
        int maxLength = password.startsWith(CredentialCipher.PREFIX)
                ? MAX_ENCRYPTED_PASSWORD_LENGTH : MAX_PASSWORD_LENGTH;
        if (password.length() > maxLength) {
            throw new BusinessException(400,
                    label + ".datasource.password 不能超过 " + maxLength + " 个字符");
        }
        return password;
    }

    /**
     * 屏蔽字符串字面量、双引号标识符与美元引用（替换为等长空格，保留字符位置）。
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
     * 屏蔽一段引号包裹的内容。
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
                index += 2;
                continue;
            }
            if (current == quote) {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
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
     * 判断单引号前是否为 E/e 前缀。
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
     * 识别美元引用起始标记（$$ 或 $tag$）。
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
     * 向缓冲追加指定数量的空格。
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
     * 美元引用标签首字符：字母或下划线。
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isTagStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * 美元引用标签字符。
     *
     * @param c 字符
     * @return 是否合法
     */
    private static boolean isTagPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 判断字符是否可作为标识符的一部分。
     *
     * @param c 字符
     * @return 是否可作为标识符字符
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 执行期 SQL 节点配置。
     * <p>
     * toString 固定隐藏密码，禁止在日志中输出原始凭据。
     *
     * @param code     节点编码
     * @param name     节点名称
     * @param url      PostgreSQL JDBC URL
     * @param username 用户名
     * @param password 密码
     * @param sql      单条 SELECT/WITH
     */
    public record SqlNode(String code, String name, String url, String username,
                          String password, String sql) {

        /**
         * 隐藏密码的字符串表示。
         *
         * @return 脱敏描述
         */
        @Override
        public String toString() {
            return "SqlNode[code=" + code + ", name=" + name + ", url=" + url
                    + ", username=" + username + ", password=***]";
        }
    }
}
