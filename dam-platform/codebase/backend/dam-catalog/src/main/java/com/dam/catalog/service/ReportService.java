package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamDataSource;
import com.dam.catalog.entity.MetadataColumn;
import com.dam.catalog.entity.MetadataSchema;
import com.dam.catalog.entity.MetadataTable;
import com.dam.catalog.entity.MetricDefinition;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.DamDataSourceMapper;
import com.dam.catalog.repository.MetadataColumnMapper;
import com.dam.catalog.repository.MetadataSchemaMapper;
import com.dam.catalog.repository.MetadataTableMapper;
import com.dam.catalog.repository.MetricDefinitionMapper;
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

    /** 单次生成 Prompt 注入的最大表数量（超出截断，避免上下文过长导致模型超时） */
    private static final int MAX_TABLES = 24;
    /** 单表注入的最大字段数量（宽表截断，避免 Prompt 过大） */
    private static final int MAX_COLUMNS_PER_TABLE = 60;
    /** 用户明确点名的关键表保留更多字段 */
    private static final int MAX_KEY_COLUMNS_PER_TABLE = 120;

    /** 需求中的高频无意义词，避免把“统计/查询”当成字段 */
    private static final Set<String> STOP_WORDS = Set.of(
            "统计", "查询", "查看", "数据", "需要", "分析", "一个", "一下", "所有", "各个",
            "本月", "上月", "今天", "昨天", "最近", "以及", "并且", "怎么", "如何", "帮我", "看看");

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
    private final DamAssetMapper assetMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetricDefinitionMapper metricMapper;
    private final MetadataSchemaMapper schemaMapper;
    private final CryptoService cryptoService;
    private final WeKnoraKnowledgeService weKnoraKnowledgeService;

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

        // 1. 检索 WeKnora 知识库，把指标解释/字段映射并入检索和 Prompt
        String knowledgeContext = retrieveKnowledge(requirement);
        String enrichedRequirement = StrUtil.isBlank(knowledgeContext) ? requirement
                : requirement + "\n\n业务知识库参考：\n" + knowledgeContext;

        // 2. 检索已同步的表结构（含中文注释）
        TableContext ctx = buildTableContext(dataSourceId, enrichedRequirement, token);
        if (ctx.tables.isEmpty()) {
            if (ctx.noMatch) {
                throw new BusinessException("没有从表名/表注释/字段注释中检索到与需求匹配的内容。请补充业务名称，或先在「元数据管理」完善表和字段注释");
            }
            throw new BusinessException("该数据源暂无已同步的表结构，请先在「元数据管理」中同步表后再生成报表");
        }

        // 3. 组装 Prompt 并调用编码智能体
        String prompt = buildPrompt(enrichedRequirement, ds.getDsType(), ctx, dataSourceId, knowledgeContext);
        String raw = callAgent(prompt, token);
        String sql = cleanSql(raw);
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("智能体未返回有效 SQL，请调整需求描述后重试");
        }
        // 生成后自检：模型只能引用上下文中真实存在的表和字段，防止编造
        validateGeneratedSql(sql, ctx);
        // 知识库命中时，让模型按照知识库口径再复核/修正一次，避免只“参考”不执行
        List<String> knowledgeTables = extractKnowledgeTables(knowledgeContext);
        if (StrUtil.isNotBlank(knowledgeContext)) {
            String reviewed = reviewSqlWithKnowledge(requirement, ds.getDsType(), ctx, dataSourceId,
                    sql, knowledgeContext, token);
            if (StrUtil.isNotBlank(reviewed)) sql = reviewed;
        }
        // 硬校验：知识库命中了明确表，但生成 SQL 完全没用这些表，则强制重生成一次
        if (!knowledgeTables.isEmpty() && !usesKnowledgeTable(sql, knowledgeTables)) {
            String retryRequirement = requirement + "\n必须使用知识库中的表：" + String.join("、", knowledgeTables)
                    + "；不能只使用其他业务表替代。";
            String retryPrompt = buildPrompt(retryRequirement, ds.getDsType(), ctx, dataSourceId, knowledgeContext);
            String retryRaw = callAgent(retryPrompt, token);
            String retrySql = cleanSql(retryRaw);
            if (StrUtil.isNotBlank(retrySql) && !retrySql.startsWith("-- 无法生成")) {
                validateGeneratedSql(retrySql, ctx);
                sql = retrySql;
            }
            if (!usesKnowledgeTable(sql, knowledgeTables)) {
                throw new BusinessException("生成的 SQL 没有使用知识库指定的表：" + String.join("、", knowledgeTables)
                        + "。请在知识库补充参考 SQL，或检查知识条目字段映射后重试");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql);
        result.put("dataSourceName", ds.getDsName());
        result.put("tableCount", ctx.totalTables);
        result.put("truncatedTables", ctx.truncated);
        result.put("tablesUsed", extractUsedTables(sql, ctx));
        result.put("params", extractParams(sql));
        result.put("knowledgeUsed", extractKnowledgeTitles(knowledgeContext));
        result.put("knowledgeTables", knowledgeTables);
        // 用户明确要求下钻/明细时，再生成下钻 SQL，避免每次多一次模型调用
        if (requirement.contains("下钻") || requirement.contains("明细")) {
            StringBuilder drillError = new StringBuilder();
            String drillSql = generateDrill(requirement, dataSourceId, sql, token, knowledgeContext, ds.getDsType(), drillError);
            if (StrUtil.isNotBlank(drillSql)) {
                result.put("drillSql", drillSql);
                result.put("drillParams", extractParams(drillSql));
            } else {
                result.put("drillError", drillError.toString());
            }
        }
        return result;
    }

    /**
     * 根据指标定义和计算公式生成指标 SQL（供指标中心 AI 分析使用）。
     * 只生成 SQL，不执行；表/字段仍走全库检索 + 白名单校验。
     */
    public Map<String, Object> generateMetricSql(MetricDefinition metric, String token) {
        if (metric == null) throw new BusinessException("指标不存在");
        if (!"COMPUTED".equalsIgnoreCase(metric.getMetricType())) {
            throw new BusinessException("手工填报指标不需要生成 SQL");
        }
        if (metric.getDataSourceId() == null) throw new BusinessException("请先为指标选择数据源");
        DamDataSource ds = dataSourceMapper.selectById(metric.getDataSourceId());
        if (ds == null) throw new BusinessException("指标数据源不存在");

        String requirement = "指标名称：" + StrUtil.blankToDefault(metric.getMetricName(), "")
                + "\n业务定义：" + StrUtil.blankToDefault(metric.getDefinition(), "")
                + "\n计算公式：" + StrUtil.blankToDefault(metric.getFormula(), "")
                + "\n来源表：" + StrUtil.blankToDefault(metric.getSourceTables(), "")
                + "\n单位：" + StrUtil.blankToDefault(metric.getUnit(), "")
                + "\n维度：" + StrUtil.blankToDefault(metric.getDimensions(), "")
                + "\n请只生成该指标的 SELECT 查询，不要生成其他指标。";
        TableContext ctx = buildTableContext(metric.getDataSourceId(), requirement, token);
        if (ctx.tables.isEmpty()) {
            if (ctx.noMatch) throw new BusinessException("没有从表名/注释中检索到该指标准备使用的表，请补充来源表或注释");
            throw new BusinessException("该数据源暂无已同步的表结构");
        }
        String prompt = buildPrompt(requirement, ds.getDsType(), ctx, metric.getDataSourceId(), "");
        String raw = callAgent(prompt, token);
        String sql = cleanSql(raw);
        if (StrUtil.isBlank(sql)) throw new BusinessException("智能体未返回有效的指标 SQL");
        validateGeneratedSql(sql, ctx);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql);
        result.put("dataSourceName", ds.getDsName());
        result.put("tablesUsed", extractUsedTables(sql, ctx));
        result.put("params", extractParams(sql));
        return result;
    }

    /** 按知识库口径复核并修正 SQL；失败保留原 SQL */
    private String reviewSqlWithKnowledge(String requirement, String dsType, TableContext ctx,
                                         Long dataSourceId, String sql, String knowledgeContext, String token) {
        try {
            String prompt = buildPrompt(requirement, dsType, ctx, dataSourceId, knowledgeContext)
                    + "\n\n当前生成的 SQL：\n" + sql
                    + "\n\n请逐条检查当前 SQL 是否严格符合上面的知识库业务口径（尤其是指标范围、来源表、费用条件、三同一有等）。"
                    + "如果不符合，请重写；如果符合，原样输出。\n"
                    + "只输出最终 SELECT，不要解释，不要 Markdown，不要输出校验过程。";
            String raw = callAgent(prompt, token);
            String reviewed = cleanSql(raw);
            if (StrUtil.isBlank(reviewed) || reviewed.startsWith("-- 无法生成")) return null;
            validateGeneratedSql(reviewed, ctx);
            return reviewed;
        } catch (Exception e) {
            log.warn("按知识库复核 SQL 失败，保留原 SQL: {}", e.getMessage());
            return null;
        }
    }

    private List<String> extractKnowledgeTitles(String knowledgeContext) {
        List<String> titles = new ArrayList<>();
        if (StrUtil.isBlank(knowledgeContext)) return titles;
        for (String line : knowledgeContext.split("\n")) {
            if (!line.startsWith("- ")) continue;
            int idx = line.indexOf('：');
            if (idx > 2) titles.add(line.substring(2, idx));
            if (titles.size() >= 5) break;
        }
        return titles;
    }

    private List<String> extractKnowledgeTables(String knowledgeContext) {
        if (StrUtil.isBlank(knowledgeContext)) return List.of();
        java.util.regex.Matcher m = Pattern.compile(
                "\\b(?:PA_Adm(?:Ext)?|PA_PatMas|PA_PatBill|DHC[A-Za-z0-9_]+|CT_[A-Za-z0-9_]+|MR_[A-Za-z0-9_]+|OE_[A-Za-z0-9_]+|RB_[A-Za-z0-9_]+)\\b")
                .matcher(knowledgeContext);
        java.util.LinkedHashSet<String> tables = new java.util.LinkedHashSet<>();
        while (m.find()) tables.add(m.group());
        return tables.stream().limit(30).toList();
    }

    private boolean usesKnowledgeTable(String sql, List<String> tables) {
        String lower = sql.toLowerCase();
        for (String table : tables) {
            if (lower.contains(table.toLowerCase())) return true;
        }
        return false;
    }

    /** 根据主报表 SQL 生成下钻明细 SQL；参数名固定建议 date/dept，便于前端按行传值 */
    private String generateDrill(String requirement, Long dataSourceId, String mainSql,
                                 String token, String knowledgeContext, String dsType, StringBuilder drillError) {
        try {
            String drillRequirement = "主报表 SQL：\n" + mainSql
                    + "\n\n用户原始需求（包含下钻字段）：\n" + requirement
                    + "\n\n请生成下钻明细 SELECT。要求：\n"
                    + "1. 明细字段必须覆盖用户要求的下钻字段；\n"
                    + "2. 用 :date、:dept 绑定主报表的日期、科室筛选；\n"
                    + "3. ORDER BY 只能使用 SELECT 中已经定义的别名或真实字段，不能引用不存在的别名；\n"
                    + "4. 只输出一条 SELECT，不要解释。";
            TableContext ctx = buildTableContext(dataSourceId, drillRequirement, token);
            if (ctx.tables.isEmpty()) {
                drillError.append("下钻 SQL 未生成：没有检索到可用表结构");
                return null;
            }
            String prompt = buildPrompt(drillRequirement, dsType, ctx, dataSourceId, knowledgeContext);
            String raw = callAgent(prompt, token);
            String drillSql = cleanSql(raw);
            if (StrUtil.isBlank(drillSql) || drillSql.startsWith("-- 无法生成")) {
                drillError.append("下钻 SQL 未生成：模型没有返回有效明细 SQL");
                return null;
            }
            try {
                validateGeneratedSql(drillSql, ctx);
                return drillSql;
            } catch (Exception first) {
                // 第一次生成了不存在的字段时，把校验错误给模型，再纠正一次
                String retryPrompt = prompt + "\n\n上一次生成的下钻 SQL 校验失败：" + first.getMessage()
                        + "\n请修正 SQL，只能使用上下文中真实存在的表和字段，重新输出一条 SELECT。";
                String retryRaw = callAgent(retryPrompt, token);
                String retrySql = cleanSql(retryRaw);
                validateGeneratedSql(retrySql, ctx);
                return retrySql;
            }
        } catch (Exception e) {
            log.warn("生成下钻 SQL 失败，保留主报表: {}", e.getMessage());
            drillError.append("下钻 SQL 校验失败：").append(e.getMessage());
            return null;
        }
    }

    /** 从 SQL 中提取 :param 命名参数，供报表体系自动生成输入框 */
    public List<Map<String, Object>> extractParams(String sql) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (StrUtil.isBlank(sql)) return params;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)").matcher(sql);
        while (m.find()) names.add(m.group(1));
        for (String name : names) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("code", name);
            p.put("label", name);
            p.put("required", true);
            p.put("type", "string");
            params.add(p);
        }
        return params;
    }

    /** 用 code 模型生成一个简短报表标题；失败时由调用方兜底 */
    public String generateTitle(String requirement, String token) {
        try {
            String prompt = "请为下面的报表需求生成一个简洁中文标题，不超过20个汉字，只输出标题，不要引号、不要解释：\n" + requirement;
            String raw = callAgent(prompt, token);
            if (StrUtil.isBlank(raw)) return null;
            String title = raw.replace("\r", " ").replace("\n", " ").replace("\"", "").replace("'", "").replace("`", "").trim();
            if (title.startsWith("标题")) title = title.substring(2).replaceFirst("^[:：\\s]+", "");
            if (title.length() > 40) title = title.substring(0, 40);
            return StrUtil.isBlank(title) ? null : title;
        } catch (Exception e) {
            log.warn("AI 生成报表标题失败: {}", e.getMessage());
            return null;
        }
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
        boolean noMatch = false;
        String joinSkeleton = "";
    }

    /** 检索数据源下已同步的表结构与字段（含中文注释），按需求做全库检索，而不是按表名取前 N 张 */
    private TableContext buildTableContext(Long dataSourceId, String requirement, String token) {
        TableContext ctx = new TableContext();
        List<MetadataTable> tables = tableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>()
                        .eq(MetadataTable::getDatasourceId, dataSourceId)
                        .orderByAsc(MetadataTable::getTableName));
        if (tables.isEmpty()) return ctx;
        ctx.totalTables = tables.size();

        // schema 名称经 schema_id 批量解析（metadata_table 无 schema_name 列）
        Set<Long> schemaIds = tables.stream().map(MetadataTable::getSchemaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> schemaNameMap = new HashMap<>();
        if (!schemaIds.isEmpty()) {
            for (MetadataSchema s : schemaMapper.selectByIds(schemaIds)) {
                if (s != null) schemaNameMap.put(s.getId(), s.getSchemaName());
            }
        }

        // 用户明确写出的表名必须先做元数据校验；缺失就明确报错，不允许模型猜
        Set<String> explicitNames = extractExplicitTableNames(requirement);
        Map<String, MetadataTable> tableByName = new HashMap<>();
        for (MetadataTable t : tables) {
            if (t.getTableName() != null) tableByName.put(t.getTableName().toLowerCase(Locale.ROOT), t);
        }
        List<String> missing = new ArrayList<>();
        Set<Long> forcedIds = new HashSet<>();
        for (String name : explicitNames) {
            MetadataTable t = tableByName.get(name.toLowerCase(Locale.ROOT));
            if (t == null) missing.add(name);
            else forcedIds.add(t.getId());
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("需求中明确提到、但元数据里没有同步的表：" + String.join(", ", missing)
                    + "。请先在「元数据管理」同步这些表，再重新生成");
        }
        // 关联条件只作为线索，不强制；元数据 FK 优先，用户条件可能与实际模型冲突
        ctx.joinSkeleton = buildJoinSkeleton(requirement, tableByName, schemaNameMap);

        // 全库字段：字段注释也参与检索，避免只靠表名找不到业务表
        Set<Long> allTableIds = tables.stream().map(MetadataTable::getId).collect(Collectors.toSet());
        Map<Long, List<MetadataColumn>> allColsMap = columnMapper.selectList(
                        new LambdaQueryWrapper<MetadataColumn>()
                                .in(MetadataColumn::getTableId, allTableIds)
                                .orderByAsc(MetadataColumn::getOrdinalPosition))
                .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId));

        List<String> tokens = queryTokens(requirement);
        Map<Long, Integer> scores = new HashMap<>();
        Set<Long> keyTableIds = new HashSet<>();
        for (MetadataTable t : tables) {
            String schemaName = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
            List<MetadataColumn> cols = allColsMap.getOrDefault(t.getId(), Collections.emptyList());
            int score = scoreTable(t, schemaName, cols, requirement, tokens);
            if (forcedIds.contains(t.getId())) score += 1000;
            if (score <= 0) continue;
            scores.put(t.getId(), score);
            if (score >= 200) keyTableIds.add(t.getId());
        }

        List<MetadataTable> selected = tables.stream()
                .filter(t -> scores.containsKey(t.getId()))
                .sorted((a, b) -> {
                    int sa = scores.getOrDefault(a.getId(), 0);
                    int sb = scores.getOrDefault(b.getId(), 0);
                    if (sb != sa) return Integer.compare(sb, sa);
                    return String.valueOf(a.getTableName()).compareToIgnoreCase(String.valueOf(b.getTableName()));
                })
                .limit(MAX_TABLES)
                .collect(Collectors.toList());
        // 关键词检索没有命中时，再做一次“目录 + 需求”的模型检索，支持只描述业务、不懂表名的用户
        if (selected.isEmpty()) {
            Set<Long> llmIds = selectTablesByLlm(tables, schemaNameMap, allColsMap, requirement, token);
            if (!llmIds.isEmpty()) {
                selected = tables.stream()
                        .filter(t -> llmIds.contains(t.getId()))
                        .limit(MAX_TABLES)
                        .collect(Collectors.toList());
            }
        }
        if (selected.isEmpty()) {
            ctx.noMatch = true;
            return ctx;
        }
        if (selected.size() < tables.size()) ctx.truncated = true;

        for (MetadataTable t : selected) {
            Map<String, Object> m = new LinkedHashMap<>();
            String schemaName = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
            List<MetadataColumn> columns = allColsMap.getOrDefault(t.getId(), Collections.emptyList());
            int cap = keyTableIds.contains(t.getId()) ? MAX_KEY_COLUMNS_PER_TABLE : MAX_COLUMNS_PER_TABLE;
            if (columns.size() > cap) {
                columns = columns.subList(0, cap);
                ctx.truncated = true;
            }
            m.put("schemaName", schemaName);
            m.put("tableName", t.getTableName());
            m.put("tableComment", t.getTableComment());
            m.put("columns", columns);
            ctx.tables.add(m);
        }
        // 资产描述是元数据页面可直接维护的表注释；覆盖 metadata_table 的旧注释，
        // 保证 Prompt、引用表展示和生成结果使用同一个最新口径。
        try {
            List<String> selectedNames = ctx.tables.stream()
                    .map(t -> String.valueOf(t.get("tableName"))).filter(StrUtil::isNotBlank).toList();
            if (!selectedNames.isEmpty()) {
                Map<String, String> comments = assetMapper.selectList(new LambdaQueryWrapper<DamAsset>()
                                .eq(DamAsset::getDataSourceId, dataSourceId)
                                .in(DamAsset::getAssetName, selectedNames))
                        .stream().filter(a -> StrUtil.isNotBlank(a.getDescription()))
                        .collect(Collectors.toMap(DamAsset::getAssetName, DamAsset::getDescription, (a, b) -> a));
                for (Map<String, Object> table : ctx.tables) {
                    String comment = comments.get(String.valueOf(table.get("tableName")));
                    if (StrUtil.isNotBlank(comment)) table.put("tableComment", comment);
                }
            }
        } catch (Exception e) {
            log.warn("加载资产表注释失败，继续使用元数据注释: {}", e.getMessage());
        }
        return ctx;
    }

    /**
     * 关键词没有命中时，把目录摘要交给模型做一次“表选择”。
     * 模型只返回表名，后端用真实元数据回填，避免模型直接编造字段。
     */
    private Set<Long> selectTablesByLlm(List<MetadataTable> tables, Map<Long, String> schemaNameMap,
                                        Map<Long, List<MetadataColumn>> colsMap,
                                        String requirement, String token) {
        if (StrUtil.isBlank(requirement) || tables.isEmpty()) return Collections.emptySet();
        try {
            StringBuilder digest = new StringBuilder();
            int limit = Math.min(tables.size(), 300);
            for (int i = 0; i < limit; i++) {
                MetadataTable t = tables.get(i);
                String schema = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
                String full = StrUtil.isNotBlank(schema) && !"public".equalsIgnoreCase(schema)
                        ? schema + "." + t.getTableName() : t.getTableName();
                digest.append(full).append(" | ").append(StrUtil.blankToDefault(t.getTableComment(), "-"));
                List<MetadataColumn> cols = colsMap.getOrDefault(t.getId(), Collections.emptyList());
                if (!cols.isEmpty()) {
                    digest.append(" | 字段：");
                    int n = Math.min(cols.size(), 12);
                    for (int j = 0; j < n; j++) {
                        MetadataColumn c = cols.get(j);
                        if (j > 0) digest.append("，");
                        digest.append(c.getColumnName());
                        if (StrUtil.isNotBlank(c.getColumnComment())) digest.append("(").append(c.getColumnComment()).append(")");
                    }
                }
                digest.append("\n");
            }
            String prompt = "你是数据目录检索助手。用户不会写 SQL，只会描述业务需求。\n"
                    + "请从下面的表目录中选出最可能包含目标数据的表，最多 8 张。\n"
                    + "只输出 JSON 字符串数组，例如 [\"schema.table\"]；没有合适的表输出 []，不要解释。\n\n"
                    + "用户需求：" + requirement + "\n\n表目录：\n" + digest;
            String raw = callAgent(prompt, token);
            if (StrUtil.isBlank(raw)) return Collections.emptySet();
            int l = raw.indexOf('[');
            int r = raw.lastIndexOf(']');
            if (l < 0 || r <= l) return Collections.emptySet();
            JsonNode arr = MAPPER.readTree(raw.substring(l, r + 1));
            if (!arr.isArray()) return Collections.emptySet();
            Set<String> names = new HashSet<>();
            for (JsonNode n : arr) {
                String name = n.asText("").replace("`", "").replace("\"", "").trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) names.add(name);
            }
            Set<Long> ids = new HashSet<>();
            for (MetadataTable t : tables) {
                String schema = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
                String table = t.getTableName() == null ? "" : t.getTableName().toLowerCase(Locale.ROOT);
                String full = StrUtil.isNotBlank(schema) ? (schema + "." + table).toLowerCase(Locale.ROOT) : table;
                if (names.contains(table) || names.contains(full)) ids.add(t.getId());
            }
            return ids;
        } catch (Exception e) {
            log.warn("报表目录模型检索失败，回退为空: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /** 从需求里的 A.x = B.y 提取被关联的表名 */
    private Set<String> extractJoinTableNames(String requirement) {
        Set<String> names = new LinkedHashSet<>();
        java.util.regex.Matcher m = Pattern.compile(
                "(?i)([A-Za-z_][\\w$]*)\\.[A-Za-z_][\\w$]*\\s*=\\s*([A-Za-z_][\\w$]*)\\.[A-Za-z_][\\w$]*").matcher(requirement == null ? "" : requirement);
        while (m.find()) {
            names.add(m.group(1));
            names.add(m.group(2));
        }
        return names;
    }

    /** 根据用户给出的关联条件生成确定性的 FROM/JOIN 骨架，避免模型替换表或把 schema 当表 */
    private String buildJoinSkeleton(String requirement, Map<String, MetadataTable> tableByName,
                                     Map<Long, String> schemaNameMap) {
        if (StrUtil.isBlank(requirement)) return "";
        java.util.regex.Matcher m = Pattern.compile(
                "(?i)([A-Za-z_][\\w$]*)\\.([A-Za-z_][\\w$]*)\\s*=\\s*([A-Za-z_][\\w$]*)\\.([A-Za-z_][\\w$]*)").matcher(requirement);
        List<String[]> joins = new ArrayList<>();
        List<String> order = new ArrayList<>();
        while (m.find()) {
            String a = m.group(1).toLowerCase(Locale.ROOT), ca = m.group(2);
            String b = m.group(3).toLowerCase(Locale.ROOT), cb = m.group(4);
            if (!tableByName.containsKey(a) || !tableByName.containsKey(b)) continue;
            joins.add(new String[]{a, ca, b, cb});
            if (!order.contains(a)) order.add(a);
            if (!order.contains(b)) order.add(b);
        }
        if (joins.isEmpty()) return "";
        // 只保留与第一个表连通的边，避免把无关表拼进来
        Map<String, String> aliases = new LinkedHashMap<>();
        int idx = 1;
        for (String n : order) aliases.put(n, "t" + idx++);
        Set<String> visited = new LinkedHashSet<>();
        String root = order.get(0);
        visited.add(root);
        StringBuilder sb = new StringBuilder();
        sb.append("FROM ").append(fullName(tableByName.get(root), schemaNameMap)).append(" ").append(aliases.get(root));
        boolean added = true;
        while (added) {
            added = false;
            for (String[] j : joins) {
                String a = j[0], b = j[2];
                if (visited.contains(a) && !visited.contains(b)) {
                    sb.append("\nJOIN ").append(fullName(tableByName.get(b), schemaNameMap)).append(" ").append(aliases.get(b))
                            .append(" ON ").append(aliases.get(a)).append(".").append(j[1])
                            .append(" = ").append(aliases.get(b)).append(".").append(j[3]);
                    visited.add(b);
                    added = true;
                } else if (visited.contains(b) && !visited.contains(a)) {
                    sb.append("\nJOIN ").append(fullName(tableByName.get(a), schemaNameMap)).append(" ").append(aliases.get(a))
                            .append(" ON ").append(aliases.get(b)).append(".").append(j[3])
                            .append(" = ").append(aliases.get(a)).append(".").append(j[1]);
                    visited.add(a);
                    added = true;
                }
            }
        }
        return sb.toString();
    }

    private String fullName(MetadataTable t, Map<Long, String> schemaNameMap) {
        if (t == null) return "";
        String schema = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
        return StrUtil.isNotBlank(schema) && !"public".equalsIgnoreCase(schema)
                ? schema + "." + t.getTableName() : t.getTableName();
    }

    /** 需求中显式写出的表名（DHC_/ODS_/DWD_ 等），用于强制校验和纳入上下文 */
    private Set<String> extractExplicitTableNames(String requirement) {
        Set<String> names = new LinkedHashSet<>();
        if (StrUtil.isBlank(requirement)) return names;
        java.util.regex.Matcher m = Pattern.compile(
                "(?i)\\b(?:DHC|ODS|DWD|DWS|ADS|DIM|BIZ|FACT|DICT)_[A-Za-z0-9_]+\\b").matcher(requirement);
        while (m.find()) names.add(m.group());
        return names;
    }

    /** 中文按 2 字滑窗、英文按单词切分，作为轻量全库检索 token */
    private List<String> queryTokens(String requirement) {
        if (StrUtil.isBlank(requirement)) return List.of();
        String req = requirement.toLowerCase(Locale.ROOT);
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = Pattern.compile("[a-z0-9_]{2,}|[\\u4e00-\\u9fa5]{2,}").matcher(req);
        while (m.find()) {
            String token = m.group();
            if (STOP_WORDS.contains(token)) continue;
            tokens.add(token);
            if (token.matches("[\\u4e00-\\u9fa5]+")) {
                for (int i = 0; i + 2 <= token.length(); i++) tokens.add(token.substring(i, i + 2));
            }
            if (tokens.size() > 120) break;
        }
        return new ArrayList<>(tokens);
    }

    /** 表名/表注释/字段名/字段注释/业务域 加权打分 */
    private int scoreTable(MetadataTable t, String schemaName, List<MetadataColumn> columns,
                           String requirement, List<String> tokens) {
        String req = requirement == null ? "" : requirement.toLowerCase(Locale.ROOT);
        String name = t.getTableName() == null ? "" : t.getTableName().toLowerCase(Locale.ROOT);
        String comment = t.getTableComment() == null ? "" : t.getTableComment().toLowerCase(Locale.ROOT);
        String domain = t.getSubjectDomain() == null ? "" : t.getSubjectDomain().toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.length() >= 4 && req.contains(name)) score += 200;
        if (comment.length() >= 2 && req.contains(comment)) score += 100;
        for (String token : tokens) {
            if (token.length() < 2) continue;
            if (name.contains(token)) score += 15;
            if (comment.contains(token)) score += 10;
            if (!domain.isEmpty() && domain.contains(token)) score += 4;
            if (!schemaName.isEmpty() && schemaName.toLowerCase(Locale.ROOT).contains(token)) score += 2;
            for (MetadataColumn c : columns) {
                String columnName = c.getColumnName() == null ? "" : c.getColumnName().toLowerCase(Locale.ROOT);
                String columnComment = c.getColumnComment() == null ? "" : c.getColumnComment().toLowerCase(Locale.ROOT);
                if (columnName.contains(token)) score += 3;
                if (columnComment.contains(token)) score += 6;
            }
        }
        return score;
    }

    /** 从生成的 SQL 中提取实际引用的表    /** 从生成的 SQL 中提取实际引用的表（与注入的表清单大小写不敏感匹配），供前端回显 */
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

    /** 生成 SQL 自检：表必须来自上下文；别名.字段必须属于对应表 */
    private void validateGeneratedSql(String sql, TableContext ctx) {
        Set<String> allowedTables = new HashSet<>();
        Map<String, Set<String>> tableColumns = new HashMap<>();
        for (Map<String, Object> t : ctx.tables) {
            String tableName = String.valueOf(t.get("tableName")).toLowerCase(Locale.ROOT);
            String schemaName = String.valueOf(t.getOrDefault("schemaName", "")).toLowerCase(Locale.ROOT);
            allowedTables.add(tableName);
            if (StrUtil.isNotBlank(schemaName) && !"null".equals(schemaName)) {
                allowedTables.add(schemaName + "." + tableName);
            }
            Set<String> cols = new HashSet<>();
            @SuppressWarnings("unchecked")
            List<MetadataColumn> columns = (List<MetadataColumn>) t.get("columns");
            for (MetadataColumn c : columns) {
                if (c.getColumnName() != null) cols.add(c.getColumnName().toLowerCase(Locale.ROOT));
            }
            tableColumns.put(tableName, cols);
        }
        // CTE 名称允许作为 FROM/JOIN 目标
        Set<String> cteNames = new HashSet<>();
        java.util.regex.Matcher cte = Pattern.compile("(?i)\\bwith\\s+([a-zA-Z_][\\w$]*)").matcher(sql);
        while (cte.find()) cteNames.add(cte.group(1).toLowerCase(Locale.ROOT));

        // 表名校验：FROM / JOIN 后面的裸表名
        java.util.regex.Matcher tm = Pattern.compile(
                "(?i)\\b(?:from|join)\\s+([`\"\\[]?[a-zA-Z_][\\w$]*(?:\\.[a-zA-Z_][\\w$]*)?[`\"\\]]?)").matcher(sql);
        Set<String> unknownTables = new LinkedHashSet<>();
        while (tm.find()) {
            String raw = tm.group(1).replace("`", "").replace("\"", "").replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            if (raw.isEmpty() || cteNames.contains(raw) || allowedTables.contains(raw)) continue;
            String shortName = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
            if (allowedTables.contains(shortName) || cteNames.contains(shortName)) continue;
            unknownTables.add(raw);
        }
        if (!unknownTables.isEmpty()) {
            List<String> available = ctx.tables.stream().map(t -> String.valueOf(t.get("tableName"))).toList();
            throw new BusinessException("AI 生成的 SQL 使用了未提供的表：" + String.join(", ", unknownTables)
                    + "。本次可用表：" + String.join(", ", available)
                    + "。请确认表名已同步，或在需求中明确表名后重试");
        }
        // 别名映射：FROM/JOIN table [AS] alias
        Map<String, String> aliasToTable = new HashMap<>();
        java.util.regex.Matcher am = Pattern.compile(
                "(?i)\\b(?:from|join)\\s+([a-zA-Z_][\\w$]*(?:\\.[a-zA-Z_][\\w$]*)?)(?:\\s+(?:as\\s+)?([a-zA-Z_][\\w$]*))?").matcher(sql);
        Set<String> keywords = Set.of("where", "on", "join", "left", "right", "inner", "outer", "full", "group",
                "order", "limit", "offset", "union", "having", "cross", "natural", "window", "fetch", "using");
        while (am.find()) {
            String table = am.group(1).toLowerCase(Locale.ROOT);
            String alias = am.group(2) == null ? null : am.group(2).toLowerCase(Locale.ROOT);
            if (alias == null || keywords.contains(alias)) continue;
            aliasToTable.put(alias, table);
        }
        Set<String> badColumns = new LinkedHashSet<>();
        java.util.regex.Matcher cm = Pattern.compile("\\b([a-zA-Z_][\\w$]*)\\.([a-zA-Z_][\\w$]*)\\b").matcher(sql);
        while (cm.find()) {
            String alias = cm.group(1).toLowerCase(Locale.ROOT);
            String column = cm.group(2).toLowerCase(Locale.ROOT);
            String table = aliasToTable.get(alias);
            if (table == null) continue;
            String shortName = table.contains(".") ? table.substring(table.lastIndexOf('.') + 1) : table;
            Set<String> cols = tableColumns.get(shortName);
            if (cols != null && !cols.contains(column)) badColumns.add(alias + "." + column);
        }
        if (!badColumns.isEmpty()) {
            throw new BusinessException("AI 生成的 SQL 使用了不存在的字段：" + String.join(", ", badColumns)
                    + "。请检查字段注释或补充表结构后重试");
        }
    }

    /** 检索 WeKnora/本地知识库，把业务解释和字段映射作为报表上下文 */
    private String retrieveKnowledge(String requirement) {
        try {
            Map<String, Object> result = weKnoraKnowledgeService.search(requirement, 5);
            Object raw = result.get("results");
            if (!(raw instanceof List<?> list) || list.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> hit)) continue;
                Object titleValue = hit.get("title");
                Object contentValue = hit.get("content");
                String title = titleValue == null ? "" : String.valueOf(titleValue);
                String content = contentValue == null ? "" : String.valueOf(contentValue);
                if (StrUtil.isBlank(content)) continue;
                sb.append("- ").append(title).append("：")
                        .append(content.length() > 1200 ? content.substring(0, 1200) : content).append("\n");
                if (++count >= 5) break;
            }
            return sb.toString().length() > 5000 ? sb.substring(0, 5000) : sb.toString();
        } catch (Exception e) {
            log.warn("报表检索知识库失败，继续使用元数据: {}", e.getMessage());
            return "";
        }
    }

    /** 组装编码智能体 Prompt */
    private String buildPrompt(String requirement, String dsType, TableContext ctx, Long dataSourceId, String knowledgeContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是医疗行业数据治理专家，擅长编写 SQL。请根据用户的报表需求和给定的数据库表结构，生成一条可直接执行的 SELECT 查询语句。\n\n");
        sb.append("最高优先级规则：\n");
        sb.append("1. 用户明确点名的表，必须在下面上下文中；只能用下面列出的表和字段。\n");
        sb.append("2. 禁止编造表名、字段名、别名和指标；不确定的字段不要猜。\n");
        sb.append("3. 如果上下文中缺少用户点名的表或字段，不要生成 SQL，只输出：-- 无法生成：缺少 <表/字段>。\n");
        sb.append("4. 生成前自检：FROM/JOIN 的每一张表、SELECT/WHERE/GROUP BY 的每一个字段，都必须能在下面找到。\n\n");
        sb.append("数据源类型：").append(StrUtil.isBlank(dsType) ? "UNKNOWN" : dsType.toUpperCase()).append("\n\n");
        // 明确 schema 与表的关系，避免模型输出 sqluser.sqluser 这类“schema 当表名”的 SQL
        Set<String> schemas = ctx.tables.stream()
                .map(t -> String.valueOf(t.getOrDefault("schemaName", "")))
                .filter(StrUtil::isNotBlank)
                .filter(s -> !"null".equalsIgnoreCase(s))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!schemas.isEmpty()) {
            sb.append("注意：schema 名是 ").append(String.join(", ", schemas))
                    .append("，schema 不是表名；表引用必须写成 schema.表名，禁止 FROM schema.schema。\n\n");
        }
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
                if (Boolean.TRUE.equals(c.getIsForeignKey())) {
                    sb.append(" FK");
                    if (StrUtil.isNotBlank(c.getReferenceTableName())) sb.append("→").append(c.getReferenceTableName());
                }
                if (StrUtil.isNotBlank(c.getColumnComment())) sb.append(" # ").append(c.getColumnComment());
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (ctx.truncated) {
            sb.append("（注：表/字段较多，以上仅列出部分表和字段，请优先使用已列出的结构）\n\n");
        }
        // 指标定义/公式：业务用户说“门诊量/收入”时，优先使用已维护的口径
        try {
            List<MetricDefinition> metrics = metricMapper.selectList(
                    new LambdaQueryWrapper<MetricDefinition>()
                            .eq(MetricDefinition::getDataSourceId, dataSourceId)
                            .eq(MetricDefinition::getStatus, 1)
                            .isNull(MetricDefinition::getDeletedAt)
                            .last("LIMIT 50"));
            if (!metrics.isEmpty()) {
                sb.append("业务指标定义（用户提到指标时，必须优先使用这里的口径/公式，不得自行发明）：\n");
                for (MetricDefinition m : metrics) {
                    sb.append("- ").append(m.getMetricName()).append("（").append(m.getMetricCode()).append("）");
                    if ("MANUAL".equalsIgnoreCase(m.getMetricType())) {
                        sb.append(" [手工填报，不能从数据库表计算]");
                    } else {
                        if (StrUtil.isNotBlank(m.getDefinition())) sb.append(" 定义：").append(m.getDefinition());
                        if (StrUtil.isNotBlank(m.getFormula())) sb.append("；公式：").append(m.getFormula());
                        if (StrUtil.isNotBlank(m.getSqlExpression())) sb.append("；参考SQL：").append(m.getSqlExpression());
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("加载指标定义失败: {}", e.getMessage());
        }
        if (StrUtil.isNotBlank(ctx.joinSkeleton)) {
            sb.append("用户提供的关联线索（仅作理解参考；若与元数据 FK 冲突，以元数据 FK 为准）：\n");
            sb.append(ctx.joinSkeleton).append("\n\n");
        }
        sb.append("元数据中的外键/引用关系（优先使用；没有 FK 时才参考下面的用户线索）：\n");
        sb.append("- 字段行中的 FK 表示外键/指针字段，优先按真实 FK 关联\n");
        sb.append("- 用户给出的关联条件仅作线索，若与 FK 冲突，以 FK 为准；不确定时不要强行使用\n\n");
        sb.append("用户给出的关联条件（仅作线索）：\n");
        java.util.regex.Matcher jm = Pattern.compile(
                "(?i)([A-Za-z_][\\w$]*\\.[A-Za-z_][\\w$]*\\s*=\\s*[A-Za-z_][\\w$]*\\.[A-Za-z_][\\w$]*)").matcher(requirement);
        boolean hasJoin = false;
        while (jm.find()) {
            sb.append("- ").append(jm.group(1)).append("\n");
            hasJoin = true;
        }
        if (!hasJoin) sb.append("- 无显式关联条件，请只使用上下文中 PK/FK 注释\n");
        if (StrUtil.isNotBlank(knowledgeContext)) {
            sb.append("业务知识库参考（用于理解业务术语、指标口径、来源表和字段映射；不能替代真实表字段校验）：\n");
            sb.append(knowledgeContext).append("\n\n");
            sb.append("约束：如果知识库明确给出了指标口径、计算公式、来源表或字段映射，必须优先按照知识库执行；\n");
            sb.append("不得用元数据或其他表的自定义口径覆盖知识库中的明确口径。\n");
            sb.append("如果知识库提供了参考 SQL，必须沿用其表、关联方式、方言语法和过滤条件，不要另起炉灶。\n\n");
        }
        sb.append("\n用户需求：").append(requirement).append("\n\n");
        sb.append("要求：\n");
        sb.append("1. 只生成一条 SELECT 语句（可含 JOIN、GROUP BY、聚合、排序、LIMIT）\n");
        sb.append("2. 表名、字段名必须严格使用上面给出的真实名称，不得编造；只能用上下文中出现的表\n");
        sb.append("3. 多表关联时优先参考字段注释中标注的 FK 外键关系\n");
        sb.append("4. 使用上述数据源类型的 SQL 方言语法\n");
        sb.append("5. 结果集行数建议控制在合理范围（如加 LIMIT 或聚合）\n");
        sb.append("6. 只输出 SQL 本身，不要任何解释、不要 markdown 代码块、不要分号以外的多余内容\n");
        sb.append("7. 如果无法在给定结构中完成，输出 -- 无法生成：缺少 <具体表或字段>，不要编造");
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
                    .timeout(Duration.ofSeconds(240))
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
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("报表 SQL 生成超时: {}", e.getMessage());
            throw new BusinessException("报表生成超时，请缩小数据源范围、减少表数量或稍后重试");
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
