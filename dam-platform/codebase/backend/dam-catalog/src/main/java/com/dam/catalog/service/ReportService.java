package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamDataSource;
import com.dam.catalog.entity.MetadataColumn;
import com.dam.catalog.entity.MetadataSchema;
import com.dam.catalog.entity.MetadataTable;
import com.dam.catalog.repository.DamDataSourceMapper;
import com.dam.catalog.repository.MetadataColumnMapper;
import com.dam.catalog.repository.MetadataSchemaMapper;
import com.dam.catalog.repository.MetadataTableMapper;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 报表智能生成与执行服务
 * <p>
 * 编排「自然语言需求 → 表结构检索 → 代码智能体生成 SQL → 只读执行」链路：
 * <ul>
 *   <li>{@link #generate}：检索指定数据源已同步的表结构（含中文注释），组装 Prompt 后调用编码智能体生成 SELECT 语句</li>
 *   <li>{@link #execute}：对生成的 SQL 做安全校验（仅 SELECT/CTE、黑名单危险关键字），
 *       经 JDBC 只读连接执行并限制返回行数</li>
 * </ul>
 * <p>
 * 安全约束：SQL 执行前必须通过白名单（SELECT/WITH 开头）与黑名单（写操作/DDL 关键字）双重校验，
 * 并通过 {@link Statement#setMaxRows} 与 {@link Statement#setQueryTimeout} 限制结果集规模与执行时长。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    /** 允许执行的 SQL 类型：仅 SELECT 与 WITH（CTE）开头 */
    private static final Pattern SAFE_SQL_PATTERN = Pattern.compile(
            "^\\s*(SELECT|WITH)\\s+.*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 禁止出现的危险关键字（写操作 / DDL / 调用存储过程等） */
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE|CALL|MERGE|REPLACE)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 单次生成 Prompt 注入的最大表数量（超出截断，避免上下文过长） */
    private static final int MAX_TABLES = 80;

    /** 存储过程调用白名单：CALL schema.proc(...) / {call ...} */
    private static final Pattern SAFE_CALL_PATTERN = Pattern.compile(
            "^\\s*(CALL|\\{call)\\s+[\\w\\.\\$#\"\\[\\]]+\\s*\\(.*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** CALL 场景下禁止出现的关键字（防拼接写操作） */
    private static final Pattern DANGEROUS_CALL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|MERGE)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 命名参数占位符（:paramName） */
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    /** 执行结果默认最大行数 */
    private static final int DEFAULT_MAX_ROWS = 500;

    private final DamDataSourceMapper dataSourceMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataSchemaMapper schemaMapper;
    private final CryptoService cryptoService;

    /** dam-agent 服务地址（报表 SQL 生成调用编码智能体） */
    @Value("${dam.agent-url:http://localhost:8089}")
    private String agentUrl;

    /** JSON 序列化（Prompt 组装 / agent 响应解析） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** agent HTTP 客户端（线程安全，类级单例复用） */
    private static final HttpClient AGENT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ============================================================
    //  SQL 生成
    // ============================================================

    /**
     * 根据自然语言需求与数据源表结构，调用编码智能体生成报表 SQL。
     *
     * @param requirement  报表需求（自然语言）
     * @param dataSourceId 数据源ID
     * @param token        当前登录 token（透传给 dam-agent 做鉴权）
     * @return {sql, dataSourceName, tableCount, truncatedTables}
     */
    public Map<String, Object> generate(String requirement, Long dataSourceId, String token) {
        if (StrUtil.isBlank(requirement)) throw new BusinessException("报表需求不能为空");
        if (dataSourceId == null) throw new BusinessException("请选择数据源");

        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");

        // 1. 检索已同步的表结构（含中文注释）
        TableContext ctx = buildTableContext(dataSourceId);
        if (ctx.tables.isEmpty()) {
            throw new BusinessException("该数据源暂无已同步的表结构，请先在「元数据管理」中同步表后再生成报表");
        }

        // 2. 组装 Prompt 并调用编码智能体
        String prompt = buildPrompt(requirement, ds.getDsType(), ctx);
        String raw = callAgent(prompt, token);
        String sql = cleanSql(raw);
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("智能体未返回有效 SQL，请调整需求描述后重试");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql);
        result.put("dataSourceName", ds.getDsName());
        result.put("tableCount", ctx.totalTables);
        result.put("truncatedTables", ctx.truncated);
        result.put("tablesUsed", extractUsedTables(sql, ctx));
        return result;
    }

    // ============================================================
    //  SQL 执行
    // ============================================================

    /**
     * 安全执行报表 SQL（仅 SELECT），返回列名与结果行。
     *
     * @param dataSourceId 数据源ID
     * @param sql          SELECT 语句
     * @param limit        最大返回行数（可空，默认 500，上限 500）
     * @return {columns, rows, rowCount, truncated}
     */
    public Map<String, Object> execute(Long dataSourceId, String sql, Integer limit) {
        if (dataSourceId == null) throw new BusinessException("请选择数据源");
        if (StrUtil.isBlank(sql)) throw new BusinessException("SQL 不能为空");

        // 1. SQL 安全校验
        String trimmed = sql.trim();
        if (!SAFE_SQL_PATTERN.matcher(trimmed).matches() || DANGEROUS_SQL_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException("SQL 存在安全风险：仅允许执行 SELECT 查询，且不能包含写操作/DDL 关键字");
        }

        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
        if (ds.getStatus() == null || ds.getStatus() != 1) throw new BusinessException("数据源已禁用");

        int maxRows = limit != null ? Math.clamp(limit, 1, DEFAULT_MAX_ROWS) : DEFAULT_MAX_ROWS;

        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                ds.getJdbcUrl(), ds.getUsername(), cryptoService.decryptStorage(ds.getPassword()));
             Statement st = conn.createStatement()) {
            // 只读连接 + 行数/超时双重兜底，防止全表拖库
            conn.setReadOnly(true);
            st.setMaxRows(maxRows + 1); // 多取一行用于判断是否被截断
            st.setQueryTimeout(60);

            try (ResultSet rs = st.executeQuery(trimmed)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                int count = 0;
                while (rs.next() && count < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), normalizeValue(rs.getObject(i)));
                    }
                    rows.add(row);
                    count++;
                }
                boolean truncated = rs.next(); // 还有下一行说明被截断
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("truncated", truncated);
                return result;
            }
        } catch (SQLException e) {
            log.warn("报表 SQL 执行失败: {}", e.getMessage());
            throw new BusinessException("SQL 执行失败: " + e.getMessage());
        }
    }

    // ============================================================
    //  带参数执行（报表体系：SQL / IRIS 存储过程）
    // ============================================================

    /**
     * 带命名参数执行只读语句（SQL 或存储过程调用）
     * <p>
     * 语句中可用 :paramName 占位，执行前转为 PreparedStatement 参数，避免字符串拼接注入。
     *
     * @param dataSourceId 数据源ID
     * @param statement    SELECT/WITH 语句，或 allowCall=true 时的 CALL proc(?) 语句
     * @param params       参数值（按名称）
     * @param limit        最大返回行数（默认/上限 500）
     * @param allowCall    是否允许存储过程调用
     * @return {columns, rows, rowCount, truncated, elapsedMs}
     */
    public Map<String, Object> executeWithParams(Long dataSourceId, String statement,
                                                 Map<String, Object> params, Integer limit,
                                                 boolean allowCall) {
        if (dataSourceId == null) throw new BusinessException("报表未绑定数据源");
        if (StrUtil.isBlank(statement)) throw new BusinessException("报表未配置查询语句");

        String trimmed = statement.trim();
        // 1. 语句安全校验：单语句 + 白名单 + 危险关键字
        if (countStatements(trimmed) > 1) {
            throw new BusinessException("仅允许执行单条语句");
        }
        boolean isCall = SAFE_CALL_PATTERN.matcher(trimmed).matches();
        if (isCall) {
            if (!allowCall) throw new BusinessException("该报表不允许执行存储过程");
            if (DANGEROUS_CALL_PATTERN.matcher(trimmed).find()) {
                throw new BusinessException("存储过程调用含非法关键字，已拦截");
            }
        } else if (!SAFE_SQL_PATTERN.matcher(trimmed).matches()
                || DANGEROUS_SQL_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException("仅允许执行 SELECT 查询或已登记的存储过程调用");
        }

        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
        if (ds.getStatus() == null || ds.getStatus() != 1) throw new BusinessException("数据源已禁用");

        int maxRows = limit != null ? Math.clamp(limit, 1, DEFAULT_MAX_ROWS) : DEFAULT_MAX_ROWS;
        // 2. 命名参数 → ?
        List<String> paramNames = new ArrayList<>();
        String jdbcSql = toPositionalParams(trimmed, paramNames);

        long start = System.currentTimeMillis();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                ds.getJdbcUrl(), ds.getUsername(), cryptoService.decryptStorage(ds.getPassword()));
             PreparedStatement ps = conn.prepareStatement(jdbcSql)) {
            conn.setReadOnly(true);
            ps.setMaxRows(maxRows + 1);
            ps.setQueryTimeout(60);
            int idx = 1;
            for (String name : paramNames) {
                Object v = params == null ? null : params.get(name);
                if (v == null) throw new BusinessException("缺少报表参数: " + name);
                ps.setObject(idx++, v);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));
                int count = 0;
                while (rs.next() && count < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) row.put(columns.get(i - 1), normalizeValue(rs.getObject(i)));
                    rows.add(row);
                    count++;
                }
                boolean truncated = rs.next();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("truncated", truncated);
                result.put("elapsedMs", System.currentTimeMillis() - start);
                return result;
            }
        } catch (SQLException e) {
            log.warn("报表执行失败: {}", e.getMessage());
            throw new BusinessException("报表执行失败: " + e.getMessage());
        }
    }

    /** 统计有效语句数（忽略引号内分号） */
    private int countStatements(String sql) {
        int count = 0;
        boolean hasContent = false;
        char quote = 0;
        for (char ch : sql.toCharArray()) {
            if (quote != 0) {
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                hasContent = true;
            } else if (ch == ';') {
                if (hasContent) count++;
                hasContent = false;
            } else if (!Character.isWhitespace(ch)) {
                hasContent = true;
            }
        }
        if (hasContent) count++;
        return count;
    }

    /** 将 :name 命名参数转为 ? 占位（跳过字符串字面量内部） */
    private String toPositionalParams(String sql, List<String> names) {
        StringBuilder sb = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                sb.append(ch);
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                sb.append(ch);
                continue;
            }
            if (ch == ':') {
                int j = i + 1;
                while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) j++;
                if (j > i + 1) {
                    names.add(sql.substring(i + 1, j));
                    sb.append('?');
                    i = j - 1;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    // ============================================================
    //  表结构检索与 Prompt 组装
    // ============================================================

    /** 表结构上下文（表 + 字段 + schema 名） */
    private static class TableContext {
        final List<Map<String, Object>> tables = new ArrayList<>();
        int totalTables = 0;
        boolean truncated = false;
    }

    /** 检索数据源下已同步的表结构与字段（含中文注释），供 AI 生成 SQL 时参考 */
    private TableContext buildTableContext(Long dataSourceId) {
        TableContext ctx = new TableContext();
        List<MetadataTable> tables = tableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>()
                        .eq(MetadataTable::getDatasourceId, dataSourceId)
                        .orderByAsc(MetadataTable::getTableName));
        if (tables.isEmpty()) return ctx;
        ctx.totalTables = tables.size();

        // 截断表数量，避免 Prompt 过长
        List<MetadataTable> selected = tables;
        if (tables.size() > MAX_TABLES) {
            selected = tables.subList(0, MAX_TABLES);
            ctx.truncated = true;
        }

        // schema 名称经 schema_id 批量解析（metadata_table 无 schema_name 列）
        Set<Long> schemaIds = selected.stream().map(MetadataTable::getSchemaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> schemaNameMap = new HashMap<>();
        if (!schemaIds.isEmpty()) {
            for (MetadataSchema s : schemaMapper.selectByIds(schemaIds)) {
                if (s != null) schemaNameMap.put(s.getId(), s.getSchemaName());
            }
        }

        // 字段按 tableId 分组
        Set<Long> tableIds = selected.stream().map(MetadataTable::getId).collect(Collectors.toSet());
        Map<Long, List<MetadataColumn>> colsMap = columnMapper.selectList(
                        new LambdaQueryWrapper<MetadataColumn>()
                                .in(MetadataColumn::getTableId, tableIds)
                                .orderByAsc(MetadataColumn::getOrdinalPosition))
                .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId));

        for (MetadataTable t : selected) {
            Map<String, Object> m = new LinkedHashMap<>();
            String schemaName = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
            m.put("schemaName", schemaName);
            m.put("tableName", t.getTableName());
            m.put("tableComment", t.getTableComment());
            m.put("columns", colsMap.getOrDefault(t.getId(), Collections.emptyList()));
            ctx.tables.add(m);
        }
        return ctx;
    }

    /** 从生成的 SQL 中提取实际引用的表（与注入的表清单大小写不敏感匹配），供前端回显 */
    private List<Map<String, Object>> extractUsedTables(String sql, TableContext ctx) {
        String lowerSql = sql.toLowerCase();
        List<Map<String, Object>> used = new ArrayList<>();
        for (Map<String, Object> t : ctx.tables) {
            String tableName = (String) t.get("tableName");
            if (StrUtil.isBlank(tableName)) continue;
            if (lowerSql.contains(tableName.toLowerCase())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tableName", tableName);
                m.put("tableComment", t.get("tableComment"));
                used.add(m);
            }
        }
        return used;
    }

    /** 组装编码智能体 Prompt */
    private String buildPrompt(String requirement, String dsType, TableContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是医疗行业数据治理专家，擅长编写 SQL。请根据用户的报表需求和给定的数据库表结构，生成一条可直接执行的 SELECT 查询语句。\n\n");
        sb.append("数据源类型：").append(StrUtil.isBlank(dsType) ? "UNKNOWN" : dsType.toUpperCase()).append("\n\n");
        sb.append("可用表结构（表名/注释 + 字段名/类型/注释，PK=主键，FK=外键）：\n");
        for (Map<String, Object> t : ctx.tables) {
            String schemaName = (String) t.get("schemaName");
            String tableName = (String) t.get("tableName");
            String tableComment = (String) t.get("tableComment");
            String fullName = StrUtil.isNotBlank(schemaName) && !"public".equalsIgnoreCase(schemaName)
                    ? schemaName + "." + tableName : tableName;
            sb.append("=== ").append(fullName);
            if (StrUtil.isNotBlank(tableComment)) sb.append("（").append(tableComment).append("）");
            sb.append(" ===\n");
            @SuppressWarnings("unchecked")
            List<MetadataColumn> cols = (List<MetadataColumn>) t.get("columns");
            for (MetadataColumn c : cols) {
                sb.append("- ").append(c.getColumnName())
                        .append(" (").append(StrUtil.isBlank(c.getColumnType()) ? "未知类型" : c.getColumnType()).append(")");
                if (Boolean.TRUE.equals(c.getIsPrimaryKey())) sb.append(" PK");
                if (Boolean.TRUE.equals(c.getIsForeignKey())) sb.append(" FK");
                if (StrUtil.isNotBlank(c.getColumnComment())) sb.append(" # ").append(c.getColumnComment());
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (ctx.truncated) {
            sb.append("（注：该数据源表数量较多，以上仅列出前 ").append(MAX_TABLES).append(" 张表）\n\n");
        }
        sb.append("用户需求：").append(requirement).append("\n\n");
        sb.append("要求：\n");
        sb.append("1. 只生成一条 SELECT 语句（可含 JOIN、GROUP BY、聚合、排序、LIMIT）\n");
        sb.append("2. 表名、字段名必须严格使用上面给出的真实名称，不得编造\n");
        sb.append("3. 多表关联时优先参考字段注释中标注的 FK 外键关系\n");
        sb.append("4. 使用上述数据源类型的 SQL 方言语法\n");
        sb.append("5. 结果集行数建议控制在合理范围（如加 LIMIT 或聚合）\n");
        sb.append("6. 只输出 SQL 本身，不要任何解释、不要 markdown 代码块、不要分号以外的多余内容");
        return sb.toString();
    }

    /** 调用 dam-agent 编码智能体（/api/agent/complete），返回其 content */
    private String callAgent(String prompt, String token) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentType", "code");
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            String json = MAPPER.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl + "/api/agent/complete"))
                    .timeout(Duration.ofSeconds(150))
                    .header("Content-Type", "application/json");
            if (StrUtil.isNotBlank(token)) builder.header("token", token);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

            HttpResponse<String> resp = AGENT_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException("智能体调用失败: HTTP " + resp.statusCode());
            }
            JsonNode node = MAPPER.readTree(resp.body());
            if (node.path("code").asInt(-1) != 200) {
                throw new BusinessException("智能体调用失败: " + node.path("message").asText("未知错误"));
            }
            return node.path("data").path("content").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用编码智能体生成 SQL 失败", e);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }
    }

    /** 清理智能体输出（去除 markdown 代码块包裹与首尾空白） */
    private String cleanSql(String content) {
        if (StrUtil.isBlank(content)) return "";
        String s = content.trim();
        s = s.replaceAll("(?s)^```[a-zA-Z]*\\s*", "");
        s = s.replaceAll("(?s)```$", "");
        return s.trim();
    }

    /** 规范化 JDBC 返回值，避免 Timestamp/Blob 等类型导致 JSON 序列化异常 */
    private Object normalizeValue(Object v) {
        if (v == null) return null;
        if (v instanceof Number || v instanceof Boolean || v instanceof String) return v;
        if (v instanceof byte[]) return "[BLOB]";
        if (v instanceof java.sql.Blob || v instanceof java.sql.Clob) return "[LOB]";
        return v.toString();
    }
}
