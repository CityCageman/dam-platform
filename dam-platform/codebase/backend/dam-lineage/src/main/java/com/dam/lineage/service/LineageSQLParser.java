package com.dam.lineage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强版SQL血缘解析器
 * <p>
 * 支持两类解析：
 * <ul>
 *   <li>{@link #parseSql}：表级血缘解析（INSERT..SELECT / CREATE TABLE AS / CTE），供宏观拓扑</li>
 *   <li>{@link #parseReportSql}：报表 SELECT 结构化解析，提取表/字段映射/JOIN/过滤/分组/计算字段，供微观精细血缘</li>
 * </ul>
 */
@Service
@Slf4j
public class LineageSQLParser {

    /** 运算符集合（算术表达式判定） */
    private static final String OPERATORS = "+-*/%";

    /**
     * 解析SQL，提取血缘关系（含字段级）
     */
    public List<LineageRelation> parseSql(String sql, String jobName, String sourceSystem) {
        List<LineageRelation> relations = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) return relations;

        String normalized = sql.replaceAll("\\s+", " ").trim();

        // 解析 INSERT INTO ... SELECT
        parseInsertSelect(normalized, sql, jobName, sourceSystem, relations);

        // 解析 CREATE TABLE ... AS SELECT
        parseCreateAsSelect(normalized, sql, jobName, sourceSystem, relations);

        // 解析 CTE (WITH ... AS)
        parseCTE(normalized, sql, jobName, sourceSystem, relations);

        return relations;
    }

    /**
     * 报表 SQL 结构化解析（SELECT 查询类）
     * <p>
     * 提取：引用表集合、SELECT 字段映射（含计算字段）、JOIN 关联条件、WHERE 过滤条件、
     * GROUP BY 分组字段、ORDER BY 排序字段。字段映射类型：
     * DIRECT-直接映射 / CALC-算术计算 / AGGREGATE-聚合 / FUNCTION-函数转换 / CASE-条件判断。
     *
     * @param sql 报表/视图 SQL
     * @return 结构化解析结果
     */
    public ParseResult parseReportSql(String sql) {
        ParseResult result = new ParseResult();
        if (sql == null || sql.trim().isEmpty()) return result;
        result.reportSql = sql.trim();

        // 去除 SQL 注释（-- 行注释 / /* 块注释），避免注释内容被误解析进表达式（如注释中的 '-' 误判为减号）
        String noComments = stripSqlComments(result.reportSql);
        String normalized = noComments.replaceAll("\\s+", " ").trim();

        // 截取 SELECT 与 FROM 之间的子句
        int fromIdx = findKeyword(normalized, "FROM", 0);
        if (fromIdx < 0) return result;
        String selectClause = normalized.substring(7, fromIdx).trim();

        // 截取 FROM 之后到 GROUP/ORDER/UNION/LIMIT/分号 的查询主体
        String fromClause = normalized.substring(fromIdx + 4).trim();
        // 0. 提取内联子查询（括号配对占位为别名，递归解析子查询到 subQueries）
        fromClause = extractSubQueries(fromClause, result);
        int stopIdx = findKeywordIndex(fromClause, Arrays.asList("GROUP BY", "ORDER BY", "UNION", "LIMIT", ";"));
        if (stopIdx >= 0) fromClause = fromClause.substring(0, stopIdx).trim();

        // 1. 解析 FROM/JOIN 表与别名
        parseTablesFromClause(fromClause, result);

        // 2. 解析 JOIN ON 关联条件
        parseJoinRelations(fromClause, result);

        // 3. 解析 SELECT 字段映射
        parseSelectFields(selectClause, result);

        // 3.1 字段级 JOIN 溯源补全（SELECT 字段命中 JOIN 键时补充关联真源）
        enrichJoinLineage(result);

        // 4. 解析 WHERE 过滤条件
        parseWhere(normalized, result);

        // 5. 解析 GROUP BY / ORDER BY
        parseGroupOrder(normalized, result);

        return result;
    }

    /**
     * 去除 SQL 注释（-- 行注释 / /* 块注释），字符串字面量内的 '--' 与 '/*' 不受影响
     *
     * @param sql 原始 SQL
     * @return 去除注释后的 SQL
     */
    private String stripSqlComments(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        boolean inStr = false;
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (inStr) {
                sb.append(c);
                if (c == '\'') inStr = false;
                i++;
                continue;
            }
            if (c == '\'') {
                inStr = true;
                sb.append(c);
                i++;
                continue;
            }
            // -- 行注释：跳过到行尾
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') i++;
                continue;
            }
            // /* 块注释：跳过到 */
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * 提取 FROM/JOIN 中的内联子查询（括号配对），占位为别名，并递归解析子查询
     * <p>
     * 例：LEFT JOIN (SELECT ... GROUP BY ...) ia ON a.PAADM_RowId = ia.DHCBCI_ADMDR
     * → 将子查询区域替换为别名 ia（保留其 JOIN/ON 结构），子查询内容递归解析存入 result.subQueries
     *
     * @param fromClause FROM 之后的原始子句
     * @param result     主查询解析结果（填充 subQueries/subQueryAliases）
     * @return 子查询占位后的 fromClause
     */
    private String extractSubQueries(String fromClause, ParseResult result) {
        // fromClause 由调用方 normalized.substring(...).trim() 生成，恒非 null，无需空值防御
        StringBuilder sb = new StringBuilder();
        int i = 0, n = fromClause.length();
        int depth = 0, start = -1;
        while (i < n) {
            char c = fromClause.charAt(i);
            if (c == '(') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String content = fromClause.substring(start + 1, i).trim();
                    if (content.toUpperCase().startsWith("SELECT")) {
                        // 提取子查询别名：(... ) AS? alias
                        String rest = fromClause.substring(i + 1);
                        Matcher am = Pattern.compile("^\\s+(?:AS\\s+)?([\\w\\u4e00-\\u9fa5]+)").matcher(rest);
                        if (am.find()) {
                            String alias = am.group(1);
                            // 递归解析子查询（子查询内可能再含子查询）
                            ParseResult inner = parseReportSql(content);
                            SubQueryRef ref = new SubQueryRef();
                            ref.alias = alias;
                            ref.subSql = content;
                            ref.inner = inner;
                            result.subQueries.add(ref);
                            result.subQueryAliases.add(alias);
                            // 占位：子查询区域替换为别名（保留 JOIN 结构供主查询解析）
                            sb.append(alias);
                            i = i + 1 + am.end(); // 跳过 ')' 与别名
                            start = -1;
                            continue;
                        }
                    }
                    start = -1;
                }
            }
            if (start < 0) sb.append(c);
            i++;
        }
        // 防御：括号未闭合时追加剩余内容
        if (start >= 0) sb.append(fromClause.substring(start));
        return sb.toString();
    }

    /** 解析 FROM 子句中的表与别名（含 JOIN） */
    private void parseTablesFromClause(String fromClause, ParseResult result) {
        // 第一个表：FROM 关键字已被剥除，fromClause 以表名开头（子查询占位别名同样识别）
        Matcher first = Pattern.compile("^([\\w.]+)(?:\\s+(?:AS\\s+)?([\\w\\u4e00-\\u9fa5]+))?", Pattern.CASE_INSENSITIVE).matcher(fromClause);
        if (first.find()) {
            String name = cleanIdentifier(first.group(1));
            // 子查询占位别名不是真实表（子查询已递归解析到 subQueries），跳过
            if (!name.contains("(") && !result.subQueryAliases.contains(name)) {
                TableRef ref = new TableRef();
                ref.name = name;
                ref.alias = first.group(2) != null ? first.group(2) : lastSegment(name);
                result.tables.add(ref);
            }
        }
        // JOIN 表
        Pattern p = Pattern.compile(
                "\\b(?:JOIN|INNER\\s+JOIN|LEFT\\s+(?:OUTER\\s+)?JOIN|RIGHT\\s+(?:OUTER\\s+)?JOIN|FULL\\s+(?:OUTER\\s+)?JOIN|CROSS\\s+JOIN)\\s+([\\w.]+)(?:\\s+(?:AS\\s+)?(\\w+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(fromClause);
        while (m.find()) {
            String tableName = cleanIdentifier(m.group(1));
            String alias = m.group(2);
            // 子查询占位别名后紧跟的 ON 关键字不能被当作别名（如 LEFT JOIN ia ON ...）；
            // 字面量在左侧调用 equalsIgnoreCase，alias 为 null 时返回 false，无需判空
            if ("ON".equalsIgnoreCase(alias)) alias = null;
            // 跳过子查询括号表名（含 '(' 视为子查询，跳过）
            if (tableName.contains("(")) continue;
            // 跳过子查询占位别名（如 LEFT JOIN ia ON ...，ia 非真实表）
            if (result.subQueryAliases.contains(tableName)) continue;
            // 去重：同表同别名只保留一条（lambda 需引用 effectively final 副本）
            final String dupTable = tableName;
            final String dupAlias = alias;
            boolean dup = result.tables.stream().anyMatch(t ->
                    t.name.equalsIgnoreCase(dupTable) && (dupAlias == null || dupAlias.equalsIgnoreCase(t.alias)));
            if (dup) continue;
            TableRef ref = new TableRef();
            ref.name = tableName;
            ref.alias = alias != null ? alias : lastSegment(tableName);
            result.tables.add(ref);
        }
    }

    /** 解析 JOIN ON 关联条件 */
    private void parseJoinRelations(String fromClause, ParseResult result) {
        // 按 JOIN 切分，提取每个 JOIN ... ON ... 中的条件
        Pattern joinPattern = Pattern.compile(
                "\\b(INNER\\s+JOIN|LEFT\\s+(?:OUTER\\s+)?JOIN|RIGHT\\s+(?:OUTER\\s+)?JOIN|FULL\\s+(?:OUTER\\s+)?JOIN|JOIN)\\s+([\\w.]+)(?:\\s+(?:AS\\s+)?(\\w+))?\\s+ON\\s+([^)]+?)(?=\\b(?:INNER|LEFT|RIGHT|FULL|CROSS)?\\s*JOIN\\b|\\bWHERE\\b|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = joinPattern.matcher(fromClause);
        while (m.find()) {
            String joinType = m.group(1).toUpperCase().replaceAll("\\s+", "_");
            String condition = m.group(4).trim();
            // 提取 a.col = b.col 形式（字段名支持中文）
            Matcher cond = Pattern.compile("([\\w\\u4e00-\\u9fa5.]+)\\s*(?:=|<>|<|>|<=|>=)\\s*([\\w\\u4e00-\\u9fa5.]+)", Pattern.CASE_INSENSITIVE).matcher(condition);
            if (cond.find()) {
                String[] left = splitQualified(cond.group(1));
                String[] right = splitQualified(cond.group(2));
                JoinRel rel = new JoinRel();
                rel.fromAlias = left[0];
                rel.fromColumn = left[1];
                rel.toAlias = right[0];
                rel.toColumn = right[1];
                rel.joinType = joinType;
                result.joins.add(rel);
            }
        }
    }

    /**
     * 字段级 JOIN 溯源补全
     * <p>
     * 当 SELECT 字段来自被 JOIN 引入的表（非主表）时，该字段的数据真源是关联条件另一端的字段。
     * 例：SELECT m.PAPMI_No AS 病人号 FROM PA_Adm a INNER JOIN PA_PatMas m ON a.PAADM_PAPMI_DR = m.PAPMI_RowId
     * 病人号取自 PA_PatMas.PAPMI_No，但 PA_PatMas 是通过 JOIN（a.PAADM_PAPMI_DR = m.PAPMI_RowId）引入的，
     * 故其真源为 JOIN 另一端的 PA_Adm.PAADM_PAPMI_DR，且 PA_PatMas 侧的关联键为 PAPMI_RowId。
     * 本方法为被 JOIN 引入表的字段补充 joinSourceTable/joinSourceColumn/joinKeyColumn，并挂 joinCondition。
     * 主表（FROM 后第一个表）字段视为直接来源，不做 JOIN 追溯。
     *
     * @param result 报表 SQL 解析结果
     */
    private void enrichJoinLineage(ParseResult result) {
        if (result.joins.isEmpty()) return;
        // 主表（驱动表）：FROM 后第一个表，其字段视为直接来源，不做 JOIN 追溯
        String mainTable = result.tables.isEmpty() ? null : result.tables.getFirst().name;
        for (ColumnRef col : result.columns) {
            if (col.sourceTable == null || col.sourceColumn == null) continue;
            // 子查询虚拟表字段：直接来源为子查询输出字段，不做 JOIN 追溯（子查询内部血缘已递归解析）
            if (!result.subQueryAliases.isEmpty() && result.subQueryAliases.contains(col.sourceTable)) continue;
            // 主表字段直接映射，不追溯
            if (col.sourceTable.equalsIgnoreCase(mainTable)) continue;
            StringBuilder condSb = new StringBuilder();
            for (JoinRel join : result.joins) {
                // 关联条件两侧解析为真实表名（JOIN 记录存别名，与 col.sourceTable 的真实表名比较）
                String fromTable = resolveTableByAlias(join.fromAlias, result);
                String toTable = resolveTableByAlias(join.toAlias, result);
                boolean colInFrom = col.sourceTable.equalsIgnoreCase(fromTable)
                        || col.sourceTable.equalsIgnoreCase(join.fromAlias);
                boolean colInTo = col.sourceTable.equalsIgnoreCase(toTable)
                        || col.sourceTable.equalsIgnoreCase(join.toAlias);
                if (!colInFrom && !colInTo) continue;

                // 关联条件原文（真实表名.字段 = 真实表名.字段）
                String cond = fromTable + "." + join.fromColumn + " = " + toTable + "." + join.toColumn;
                if (!condSb.isEmpty()) condSb.append(" AND ");
                condSb.append(cond);

                // 字段来自被 JOIN 引入的表（非主表）→ 真源 = JOIN 条件另一端字段，本表侧 JOIN 键待关联
                if (col.joinSourceTable == null) {
                    if (colInFrom) {
                        col.joinSourceTable = toTable;
                        col.joinSourceColumn = join.toColumn;
                        col.joinKeyColumn = join.fromColumn;
                    } else {
                        col.joinSourceTable = fromTable;
                        col.joinSourceColumn = join.fromColumn;
                        col.joinKeyColumn = join.toColumn;
                    }
                }
            }
            if (!condSb.isEmpty()) {
                col.joinCondition = condSb.toString();
            }
        }
    }

    /** 解析 SELECT 字段映射（含计算字段识别） */
    private void parseSelectFields(String selectClause, ParseResult result) {
        List<String> items = splitSelectItems(selectClause);
        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;
            ColumnRef col = analyzeSelectItem(item, result);
            if (col != null) result.columns.add(col);
        }
    }

    /** 分析单个 SELECT 项：识别别名/来源字段/映射类型/计算表达式 */
    private ColumnRef analyzeSelectItem(String item, ParseResult result) {
        if (isConstant(item)) return null;
        ColumnRef col = new ColumnRef();

        // 提取别名（AS xxx 或尾部裸别名，支持中文）
        Matcher as = Pattern.compile("\\s+AS\\s+([\\w\\u4e00-\\u9fa5$]+)$", Pattern.CASE_INSENSITIVE).matcher(item);
        if (as.find()) {
            col.alias = as.group(1);
            item = item.substring(0, as.start()).trim();
        } else {
            // 无 AS：可能尾部直接是别名（如 d.dept_name 科室）
            Matcher tail = Pattern.compile("^[\\w.]+\\s+([\\w\\u4e00-\\u9fa5$]+)$").matcher(item);
            if (tail.find()) {
                col.alias = tail.group(1);
                item = tail.group(0).substring(0, tail.start(1)).trim();
            }
        }

        col.expression = cleanIdentifier(item);
        col.dependsOn = new ArrayList<>();

        // 提取表达式中引用的 表.字段 对（支持中文字段名；用自定义边界替代 \b 以兼容中文）
        Matcher fieldRef = Pattern.compile("(?<![\\w\\u4e00-\\u9fa5])([\\w\\u4e00-\\u9fa5]+)\\.([\\w\\u4e00-\\u9fa5]+)(?![\\w\\u4e00-\\u9fa5])").matcher(item);
        String firstTable = null;
        String firstColumn = null;
        while (fieldRef.find()) {
            String t = fieldRef.group(1);
            String c = fieldRef.group(2);
            col.dependsOn.add(t + "." + c);
            if (firstTable == null) { firstTable = t; firstColumn = c; }
        }

        // 识别表达式类型
        String upper = item.toUpperCase();
        boolean hasOperator = containsOperator(item);
        boolean hasFunction = item.contains("(") && item.contains(")");

        if (upper.contains("CASE") && upper.contains("WHEN")) {
            col.mappingType = "CASE";
            col.calcType = "CASE";
        } else if (hasFunction && isAggregateFunction(item)) {
            col.mappingType = "AGGREGATE";
            col.calcType = "AGGREGATE";
            col.transformFunction = extractFunctionName(item);
        } else if (hasOperator) {
            col.mappingType = "CALC";
            col.calcType = "ARITHMETIC";
        } else if (hasFunction) {
            col.mappingType = "FUNCTION";
            col.calcType = "FUNCTION";
            col.transformFunction = extractFunctionName(item);
        } else {
            col.mappingType = "DIRECT";
            col.calcType = "DIRECT";
        }

        // 归属表：从依赖字段首项解析（优先取非主表的别名，简化用首项）
        if (firstTable != null) {
            col.sourceTable = resolveTableByAlias(firstTable, result);
            col.sourceColumn = firstColumn;
        }
        // 无别名时目标字段名取表达式本身
        if (col.alias == null) {
            col.alias = col.sourceColumn != null ? col.sourceColumn : item;
        }
        return col;
    }

    /** 别名解析为真实表名 */
    private String resolveTableByAlias(String aliasOrTable, ParseResult result) {
        for (TableRef t : result.tables) {
            if (aliasOrTable.equalsIgnoreCase(t.alias) || aliasOrTable.equalsIgnoreCase(t.name)) {
                return t.name;
            }
        }
        return aliasOrTable;
    }

    /** 解析 WHERE 过滤条件 */
    private void parseWhere(String sql, ParseResult result) {
        int whereIdx = findKeyword(sql, "WHERE", 0);
        if (whereIdx < 0) return;
        String whereClause = sql.substring(whereIdx + 5).trim();
        int stopIdx = findKeywordIndex(whereClause, Arrays.asList("GROUP BY", "ORDER BY", "UNION", "LIMIT", ";"));
        if (stopIdx >= 0) whereClause = whereClause.substring(0, stopIdx).trim();
        // 按 AND/OR 拆分顶层条件
        List<String> conds = splitTopLevelConditions(whereClause);
        result.filters.addAll(conds);
    }

    /** 解析 GROUP BY / ORDER BY */
    private void parseGroupOrder(String sql, ParseResult result) {
        int groupIdx = findKeyword(sql, "GROUP BY", 0);
        if (groupIdx >= 0) {
            String groupClause = sql.substring(groupIdx + 8).trim();
            int stopIdx = findKeywordIndex(groupClause, Arrays.asList("ORDER BY", "UNION", "LIMIT", ";"));
            if (stopIdx >= 0) groupClause = groupClause.substring(0, stopIdx).trim();
            result.groupBy.addAll(Arrays.stream(groupClause.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList());
        }
        int orderIdx = findKeyword(sql, "ORDER BY", 0);
        if (orderIdx >= 0) {
            String orderClause = sql.substring(orderIdx + 8).trim();
            int stopIdx = findKeywordIndex(orderClause, Arrays.asList("UNION", "LIMIT", ";"));
            if (stopIdx >= 0) orderClause = orderClause.substring(0, stopIdx).trim();
            result.orderBy.addAll(Arrays.stream(orderClause.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList());
        }
    }

    // ==================== 通用解析工具 ====================

    /** 判断是否为聚合函数（SUM/AVG/COUNT/MAX/MIN） */
    private boolean isAggregateFunction(String item) {
        String upper = item.toUpperCase();
        return upper.matches("(?s).*\\b(SUM|AVG|COUNT|MAX|MIN)\\s*\\(.*");
    }

    /** 提取函数名 */
    private String extractFunctionName(String item) {
        Matcher m = Pattern.compile("\\b(SUM|AVG|COUNT|MAX|MIN|CONCAT|COALESCE|IFNULL|NVL|SUBSTRING|CONVERT|CAST|ROUND|ABS|LOWER|UPPER)\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(item);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    /** 是否包含算术运算符（排除字符串字面量内的符号） */
    private boolean containsOperator(String item) {
        boolean inStr = false;
        for (char c : item.toCharArray()) {
            if (c == '\'') inStr = !inStr;
            if (inStr) continue;
            if (OPERATORS.indexOf(c) >= 0) return true;
        }
        return false;
    }

    /** 按逗号分割SELECT项（处理函数内的逗号） */
    private List<String> splitSelectItems(String select) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder current = new StringBuilder();
        for (char c : select.toCharArray()) {
            if (c == '\'') inStr = !inStr;
            if (inStr) { current.append(c); continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                items.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.toString().trim().isEmpty()) {
            items.add(current.toString());
        }
        return items;
    }

    /** 按 AND/OR 拆分顶层条件 */
    private List<String> splitTopLevelConditions(String clause) {
        List<String> conds = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c == '\'') inStr = !inStr;
            if (inStr) { current.append(c); continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            // 顶层检测 AND / OR（单词边界）
            if (depth == 0 && i + 2 < clause.length() && Character.isWhitespace(clause.charAt(i))) {
                String sub = clause.substring(i).trim();
                if (sub.startsWith("AND ") || sub.startsWith("OR ")) {
                    String part = current.toString().trim();
                    if (!part.isEmpty()) conds.add(part);
                    // 跳过关键字
                    String kw = sub.startsWith("AND") ? "AND" : "OR";
                    i += kw.length();
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) conds.add(tail);
        return conds;
    }

    /** 在文本中查找关键字位置（忽略大小写，从 start 开始） */
    private int findKeyword(String text, String keyword, int start) {
        return findKeywordIndex(text.substring(start), Collections.singletonList(keyword)) < 0
                ? -1 : start + findKeywordIndex(text.substring(start), Collections.singletonList(keyword));
    }

    /** 查找多个关键字中最早出现的位置（忽略大小写） */
    private int findKeywordIndex(String text, List<String> keywords) {
        int min = -1;
        String upper = text.toUpperCase();
        for (String kw : keywords) {
            int idx = upper.indexOf(kw.toUpperCase());
            if (idx >= 0 && (min < 0 || idx < min)) min = idx;
        }
        return min;
    }

    /** 解析限定名 a.b → [a, b] */
    private String[] splitQualified(String qualified) {
        String q = cleanIdentifier(qualified);
        int dot = q.indexOf('.');
        return dot >= 0 ? new String[]{q.substring(0, dot).trim(), q.substring(dot + 1).trim()} : new String[]{q, q};
    }

    /** 取表名的最后一段（去掉 schema 前缀） */
    private String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** 清理标识符引号 */
    private String cleanIdentifier(String id) {
        if (id == null) return null;
        return id.replaceAll("[`\"\\[\\]]", "").trim();
    }

    /** 检查是否为常量表达式 */
    private boolean isConstant(String expr) {
        String trimmed = expr.trim();
        if (trimmed.matches("^\\d+(\\.\\d+)?$")) return true;
        if (trimmed.matches("^'.*'$")) return true;
        return trimmed.equals("*");
    }

    // ==================== 原有 INSERT/CREATE/CTE 解析 ====================

    private void parseInsertSelect(String sql, String rawSql, String jobName, String sourceSystem,
                                   List<LineageRelation> relations) {
        Pattern pattern = Pattern.compile(
                "INSERT\\s+(INTO\\s+)?(\\w+(\\.\\w+)?)\\s*(?:\\(([^)]+)\\))?\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+(\\.\\w+)?)(?:\\s+(?:AS\\s+)?(\\w+))?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String targetTable = matcher.group(2);
            String targetColumnsStr = matcher.group(4);
            String selectClause = matcher.group(5);
            String sourceTable = matcher.group(6);
            String sourceAlias = matcher.group(8);

            relations.add(new LineageRelation(
                    sourceTable, targetTable, null, null, rawSql, jobName, sourceSystem, "table"));

            List<FieldMapping> fieldMappings = extractFieldMappings(selectClause,
                    targetColumnsStr != null ? splitColumns(targetColumnsStr) : null,
                    sourceAlias);
            for (FieldMapping fm : fieldMappings) {
                relations.add(new LineageRelation(
                        sourceTable, targetTable,
                        fm.sourceField(), fm.targetField(),
                        rawSql, jobName, sourceSystem, "field"));
            }
        }
    }

    private void parseCreateAsSelect(String sql, String rawSql, String jobName, String sourceSystem,
                                     List<LineageRelation> relations) {
        Pattern pattern = Pattern.compile(
                "CREATE\\s+TABLE\\s+(\\w+(\\.\\w+)?)\\s+AS\\s+SELECT\\s+(.+?)\\s+FROM\\s+(\\w+(\\.\\w+)?)(?:\\s+(?:AS\\s+)?(\\w+))?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String targetTable = matcher.group(1);
            String selectClause = matcher.group(3);
            String sourceTable = matcher.group(4);
            String sourceAlias = matcher.group(6);

            relations.add(new LineageRelation(
                    sourceTable, targetTable, null, null, rawSql, jobName, sourceSystem, "table"));

            List<FieldMapping> fieldMappings = extractFieldMappings(selectClause, null, sourceAlias);
            for (FieldMapping fm : fieldMappings) {
                relations.add(new LineageRelation(
                        sourceTable, targetTable,
                        fm.sourceField(), fm.targetField(),
                        rawSql, jobName, sourceSystem, "field"));
            }
        }
    }

    private void parseCTE(String sql, String rawSql, String jobName, String sourceSystem,
                          List<LineageRelation> relations) {
        Pattern pattern = Pattern.compile(
                "WITH\\s+(\\w+)\\s+AS\\s+\\(\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+(\\.\\w+)?)(?:\\s+(?:AS\\s+)?(\\w+))?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String cteName = matcher.group(1);
            String selectClause = matcher.group(2);
            String sourceTable = matcher.group(3);
            String sourceAlias = matcher.group(5);

            relations.add(new LineageRelation(
                    sourceTable, cteName, null, null, rawSql, jobName, sourceSystem, "cte"));

            List<FieldMapping> fieldMappings = extractFieldMappings(selectClause, null, sourceAlias);
            for (FieldMapping fm : fieldMappings) {
                relations.add(new LineageRelation(
                        sourceTable, cteName,
                        fm.sourceField(), fm.targetField(),
                        rawSql, jobName, sourceSystem, "cte_field"));
            }
        }
    }

    private List<FieldMapping> extractFieldMappings(String selectClause,
                                                    List<String> targetColumns,
                                                    String defaultTableAlias) {
        List<FieldMapping> mappings = new ArrayList<>();
        if (selectClause == null || selectClause.trim().isEmpty()) return mappings;

        String select = selectClause.trim();
        List<String> selectItems = splitSelectItems(select);

        List<String> targetColNames = targetColumns != null ? targetColumns : new ArrayList<>();

        for (int i = 0; i < selectItems.size(); i++) {
            String item = selectItems.get(i).trim();
            if (item.isEmpty()) continue;
            if (isConstant(item)) continue;

            String sourceField;
            String targetField = null;

            Matcher aliasMatcher = Pattern.compile("\\s+(?:AS\\s+)?(\\w+)$", Pattern.CASE_INSENSITIVE).matcher(item);
            if (aliasMatcher.find()) {
                targetField = aliasMatcher.group(1);
                item = item.substring(0, aliasMatcher.start()).trim();
            }

            if (item.contains(".")) {
                String[] parts = item.split("\\.");
                sourceField = parts[parts.length - 1].trim();
            } else {
                sourceField = item.trim();
            }

            sourceField = extractFieldFromFunction(sourceField);
            if (sourceField == null) continue;

            if (targetField == null && i < targetColNames.size()) {
                targetField = targetColNames.get(i);
            }
            if (targetField == null) {
                targetField = sourceField;
            }

            sourceField = cleanIdentifier(sourceField);
            targetField = cleanIdentifier(targetField);

            mappings.add(new FieldMapping(sourceField, targetField));
        }

        return mappings;
    }

    private String extractFieldFromFunction(String field) {
        if (field == null) return null;
        if (field.contains("(") && field.contains(")")) {
            Matcher m = Pattern.compile("\\(\\s*(\\w+)\\s*\\)").matcher(field);
            if (m.find()) return m.group(1);
            return null;
        }
        return field;
    }

    private List<String> splitColumns(String columnsStr) {
        return Arrays.stream(columnsStr.split(","))
                .map(String::trim)
                .map(this::cleanIdentifier)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ==================== 数据结构 ====================

    /** 字段级映射记录 */
    public record FieldMapping(String sourceField, String targetField) {}

    /** 血缘关系记录（含字段级） */
    public record LineageRelation(
            String sourceTable,
            String targetTable,
            String sourceField,
            String targetField,
            String sql,
            String jobName,
            String sourceSystem,
            String lineageType
    ) {}

    /** 引用的表 */
    public static class TableRef {
        public String name;
        public String alias;
    }

    /** JOIN 关联 */
    public static class JoinRel {
        public String fromAlias;
        public String fromColumn;
        public String toAlias;
        public String toColumn;
        public String joinType;
    }

    /** SELECT 字段映射 */
    public static class ColumnRef {
        /** 目标字段名（别名） */
        public String alias;
        /** 完整表达式 */
        public String expression;
        /** 来源表名（解析出的首个依赖字段所属表） */
        public String sourceTable;
        /** 来源字段名 */
        public String sourceColumn;
        /** 映射类型：DIRECT/CALC/AGGREGATE/FUNCTION/CASE */
        public String mappingType;
        /** 计算类型：DIRECT/ARITHMETIC/AGGREGATE/CASE/FUNCTION */
        public String calcType;
        /** 转换函数名（如 SUM/AVG/CONCAT） */
        public String transformFunction;
        /** 依赖字段表达式列表（如 [f.fee_total, f.refund_total]） */
        public List<String> dependsOn;
        /** JOIN 关联真源表名（字段来自被 JOIN 引入的表时，关联条件另一端的真实表名） */
        public String joinSourceTable;
        /** JOIN 关联真源字段（关联条件另一端的字段名） */
        public String joinSourceColumn;
        /** JOIN 键本表侧字段（本表被 JOIN 引入时的关联键，如 PA_PatMas.PAPMI_RowId） */
        public String joinKeyColumn;
        /** JOIN 关联条件原文（如 PA_Adm.PAADM_PAPMI_DR = PA_PatMas.PAPMI_RowId） */
        public String joinCondition;
    }

    /** 报表 SQL 结构化解析结果 */
    public static class ParseResult {
        public String reportSql;
        public List<TableRef> tables = new ArrayList<>();
        public List<ColumnRef> columns = new ArrayList<>();
        public List<JoinRel> joins = new ArrayList<>();
        public List<String> filters = new ArrayList<>();
        public List<String> groupBy = new ArrayList<>();
        public List<String> orderBy = new ArrayList<>();
        /** 内联子查询引用列表 */
        public List<SubQueryRef> subQueries = new ArrayList<>();
        /** 内联子查询别名集合（大小写不敏感） */
        public Set<String> subQueryAliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    /** 内联子查询引用（FROM/JOIN 中的 SELECT 子查询） */
    public static class SubQueryRef {
        /** 子查询别名（如 ia） */
        public String alias;
        /** 子查询 SQL 原文 */
        public String subSql;
        /** 子查询递归解析结果 */
        public ParseResult inner;
    }
}
