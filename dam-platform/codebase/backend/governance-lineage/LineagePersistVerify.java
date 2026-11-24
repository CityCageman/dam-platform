import com.dam.lineage.service.LineageSQLParser;
import java.util.*;

/**
 * 临时验证类：模拟 LineagePersistService 新落库模型（值来源主边 + JOIN 定位边 + 子查询穿透），
 * 不依赖 Spring/数据库，直接推导解析结果会落成哪些血缘边（验证后删除）
 */
public class LineagePersistVerify {

    public static void main(String[] args) {
        LineageSQLParser parser = new LineageSQLParser();

        String sql = "SELECT\n" +
                "  m.PAPMI_No          AS 病人号,\n" +
                "  m.PAPMI_Name        AS 姓名,\n" +
                "  d.CTLOC_Desc          AS 就诊科室,\n" +
                "  c.CTPCP_Desc         AS 就诊医生,\n" +
                "  a.PAADM_AdmDate     AS 就诊日期,\n" +
                "  ia.总金额           AS 发票总金额,\n" +
                "  ia.自付金额,\n" +
                "  ia.记账金额,\n" +
                "  ia.折扣金额\n" +
                "FROM PA_Adm a\n" +
                "INNER JOIN PA_PatMas m\n" +
                "  ON a.PAADM_PAPMI_DR = m.PAPMI_RowId\n" +
                "LEFT JOIN CT_Loc d\n" +
                "  ON a.PAADM_DepCode_DR = d.CTLOC_RowId\n" +
                "LEFT JOIN CT_CareProv c\n" +
                "  ON a.PAADM_AdmDocCodeDR = c.CTPCP_RowId\n" +
                "LEFT JOIN (\n" +
                "  SELECT\n" +
                "    bci.DHCBCI_ADMDR,\n" +
                "    SUM(COALESCE(prt.PRT_Acount, 0))      AS 总金额,\n" +
                "    SUM(COALESCE(prt.PRT_PatientShare, 0)) AS 自付金额,\n" +
                "    SUM(COALESCE(prt.PRT_PayorShare, 0))  AS 记账金额,\n" +
                "    SUM(COALESCE(prt.PRT_DiscAmount, 0))  AS 折扣金额\n" +
                "  FROM DHC_BillConINV bci\n" +
                "  LEFT JOIN DHC_INVPRT prt\n" +
                "    ON bci.DHCBCI_INVDR = prt.PRT_Rowid\n" +
                "  GROUP BY bci.DHCBCI_ADMDR\n" +
                ") ia\n" +
                "  ON a.PAADM_RowId = ia.DHCBCI_ADMDR\n" +
                "WHERE a.PAADM_AdmDate BETWEEN '2026-07-02'\n" +
                "                           AND '2026-07-02'\n" +
                "      AND a.PAADM_Type='O'\n" +
                "      AND a.PAADM_VisitStatus='A'\n" +
                "ORDER BY a.PAADM_AdmDate, a.PAADM_RowId";

        LineageSQLParser.ParseResult pr = parser.parseReportSql(sql);

        // ========== 模拟第5步：表节点 + 分层边（name/alias -> 真实表名） ==========
        Map<String, String> cache = new HashMap<>();
        LinkedHashSet<String> tableNames = new LinkedHashSet<>();
        collectAllTables(pr, tableNames);
        collectAllAliases(pr, cache);

        System.out.println("===== 表节点注册（" + tableNames.size() + "） =====");
        for (String tn : tableNames) {
            System.out.println("  TABLE: " + tn);
            cache.put(tn, tn);
        }
        System.out.println("===== 分层边 hierarchy（每表 → 报表） =====");
        for (String tn : tableNames) {
            System.out.println("  " + tn + " → 门诊工作量报表");
        }

        // ========== 模拟第6步：字段级血缘 + 计算依赖 ==========
        System.out.println("===== 字段级血缘 =====");
        Set<String> joinEdgeKeys = new HashSet<>();
        for (LineageSQLParser.ColumnRef col : pr.columns) {
            if (col.sourceTable == null || col.sourceColumn == null) continue;

            // 子查询穿透
            LineageSQLParser.ColumnRef resolved = col;
            boolean viaSubQuery = false;
            while (isSubQueryAlias(pr, resolved.sourceTable)) {
                LineageSQLParser.ColumnRef inner = findColumnInSubQuery(pr, resolved.sourceTable, resolved.sourceColumn);
                if (inner == null) break;
                resolved = inner;
                viaSubQuery = true;
            }
            String srcNode = cache.get(resolved.sourceTable);
            if (srcNode == null) {
                System.out.println("  [跳过] 报表." + col.alias + " ← " + resolved.sourceTable + "." + resolved.sourceColumn + "（无法解析节点）");
                continue;
            }

            String mappingType = viaSubQuery ? resolved.mappingType : col.mappingType;
            String calcType = viaSubQuery ? resolved.calcType : col.calcType;
            String transformExpr = viaSubQuery ? resolved.expression : col.expression;
            String transformFn = viaSubQuery ? resolved.transformFunction : col.transformFunction;
            List<String> dependsOn = viaSubQuery && resolved.dependsOn != null ? resolved.dependsOn : col.dependsOn;
            String joinCond = buildEffectiveJoinCondition(pr, col, resolved, viaSubQuery);

            // A 主边
            System.out.println("  [主边] 报表." + col.alias + " ← " + srcNode + "." + resolved.sourceColumn
                    + " [" + mappingType + "]"
                    + (transformFn != null ? " 转换=" + transformFn : "")
                    + (joinCond != null ? " 关联=" + joinCond : ""));

            // B 定位边
            if (resolved.joinSourceTable != null && resolved.joinSourceColumn != null && resolved.joinKeyColumn != null) {
                String joinSrcNode = cache.get(resolved.joinSourceTable);
                if (joinSrcNode != null && !(joinSrcNode.equals(srcNode)
                        && resolved.joinSourceColumn.equalsIgnoreCase(resolved.joinKeyColumn))) {
                    String edgeKey = srcNode + "|" + resolved.joinKeyColumn + "|" + joinSrcNode + "|" + resolved.joinSourceColumn;
                    if (joinEdgeKeys.add(edgeKey)) {
                        System.out.println("  [定位边] " + srcNode + "." + resolved.joinKeyColumn
                                + " → " + joinSrcNode + "." + resolved.joinSourceColumn + " [JOIN]");
                    }
                }
            }

            // C 计算依赖
            if (!"DIRECT".equals(mappingType)) {
                List<String> depNames = new ArrayList<>();
                if (dependsOn != null) {
                    for (String dep : dependsOn) {
                        int dot = dep.indexOf('.');
                        depNames.add(dot >= 0 ? dep.substring(dot + 1) : dep);
                    }
                }
                System.out.println("  [计算依赖] 字段=" + col.alias + " 类型=" + calcType
                        + " 表达式=" + transformExpr + " 依赖=" + depNames);
            }
        }
    }

    static void collectAllTables(LineageSQLParser.ParseResult pr, Set<String> out) {
        if (pr == null) return;
        if (pr.tables != null) for (LineageSQLParser.TableRef t : pr.tables) out.add(t.name);
        if (pr.subQueries != null) for (LineageSQLParser.SubQueryRef sub : pr.subQueries) collectAllTables(sub.inner, out);
    }

    static void collectAllAliases(LineageSQLParser.ParseResult pr, Map<String, String> cache) {
        if (pr == null) return;
        if (pr.tables != null) for (LineageSQLParser.TableRef t : pr.tables) cache.put(t.alias, t.name);
        if (pr.subQueries != null) for (LineageSQLParser.SubQueryRef sub : pr.subQueries) collectAllAliases(sub.inner, cache);
    }

    static boolean isSubQueryAlias(LineageSQLParser.ParseResult pr, String alias) {
        if (pr == null || alias == null) return false;
        if (pr.subQueryAliases.contains(alias)) return true;
        if (pr.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
                if (isSubQueryAlias(sub.inner, alias)) return true;
            }
        }
        return false;
    }

    static LineageSQLParser.ColumnRef findColumnInSubQuery(LineageSQLParser.ParseResult pr, String subAlias, String columnName) {
        if (pr == null || pr.subQueries == null || columnName == null) return null;
        for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
            if (subAlias != null && subAlias.equalsIgnoreCase(sub.alias) && sub.inner != null) {
                for (LineageSQLParser.ColumnRef c : sub.inner.columns) {
                    if (columnName.equalsIgnoreCase(c.alias)) return c;
                }
                for (LineageSQLParser.ColumnRef c : sub.inner.columns) {
                    if (columnName.equalsIgnoreCase(c.sourceColumn)) return c;
                }
            }
            LineageSQLParser.ColumnRef found = findColumnInSubQuery(sub.inner, subAlias, columnName);
            if (found != null) return found;
        }
        return null;
    }

    static String resolveTableName(String aliasOrTable, LineageSQLParser.ParseResult pr) {
        if (pr.tables != null) {
            for (LineageSQLParser.TableRef t : pr.tables) {
                if (aliasOrTable.equalsIgnoreCase(t.alias) || aliasOrTable.equalsIgnoreCase(t.name)) return t.name;
            }
        }
        return aliasOrTable;
    }

    static String buildEffectiveJoinCondition(LineageSQLParser.ParseResult pr, LineageSQLParser.ColumnRef col,
                                              LineageSQLParser.ColumnRef resolved, boolean viaSubQuery) {
        List<String> conds = new ArrayList<>();
        if (resolved.joinCondition != null && !resolved.joinCondition.isEmpty()) conds.add(resolved.joinCondition);
        if (viaSubQuery && pr.joins != null && col.sourceTable != null) {
            String outerAlias = col.sourceTable;
            for (LineageSQLParser.JoinRel join : pr.joins) {
                if (outerAlias.equalsIgnoreCase(join.fromAlias) || outerAlias.equalsIgnoreCase(join.toAlias)) {
                    String from = resolveTableName(join.fromAlias, pr);
                    String to = resolveTableName(join.toAlias, pr);
                    conds.add(from + "." + join.fromColumn + " = " + to + "." + join.toColumn);
                }
            }
        }
        return conds.isEmpty() ? null : String.join(" AND ", conds);
    }
}
