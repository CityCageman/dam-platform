import com.dam.lineage.service.LineageSQLParser;
import java.util.*;

/**
 * 临时验证类：模拟 LineagePersistService.buildTableLinks（表级血缘传导链路构建）+ getLineageGraph 多跳收集
 * 验证：门诊工作量报表 ← PA_Adm ← CT_Loc / PA_PatMas / CT_CareProv / DHC_BillConINV ← DHC_INVPRT
 */
public class GraphVerify {

    /** 表级链路记录 */
    static class Link {
        String source, target, relationType, transformRule;
        int layerLevel;
        Link(String s, String t, String r, int l, String tr) { source = s; target = t; relationType = r; layerLevel = l; transformRule = tr; }
        @Override public String toString() { return "  " + source + " → " + target + " [" + relationType + ", L" + layerLevel + "] " + (transformRule != null ? transformRule.substring(0, Math.min(40, transformRule.length())) : ""); }
    }

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
                "WHERE a.PAADM_AdmDate BETWEEN '2026-07-02' AND '2026-07-02'\n" +
                "ORDER BY a.PAADM_AdmDate, a.PAADM_RowId";

        LineageSQLParser.ParseResult pr = parser.parseReportSql(sql);

        System.out.println("===== 表级传导链路（buildTableLinks 模拟） =====");
        for (Link link : buildTableLinks(pr)) {
            System.out.println(link);
        }
        System.out.println();
        System.out.println("预期链路（数据流）：");
        System.out.println("  PA_Adm → REPORT（应用层）");
        System.out.println("  PA_PatMas → PA_Adm / CT_Loc → PA_Adm / CT_CareProv → PA_Adm / DHC_BillConINV → PA_Adm（物理层，JOIN 引入）");
        System.out.println("  DHC_INVPRT → DHC_BillConINV（子查询内部 JOIN）");
    }

    /** 模拟 LineagePersistService.buildTableLinks */
    static List<Link> buildTableLinks(LineageSQLParser.ParseResult pr) {
        List<Link> links = new ArrayList<>();
        if (pr == null || pr.tables.isEmpty()) return links;
        String mainTable = pr.tables.get(0).name;
        Set<String> processed = new HashSet<>();
        processed.add(mainTable.toLowerCase());
        links.add(new Link(mainTable, "REPORT", "DEPEND", 1, pr.reportSql));
        if (pr.joins != null) {
            for (LineageSQLParser.JoinRel join : pr.joins) {
                addJoinLink(pr, join, links, processed);
            }
        }
        if (pr.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
                collectInnerJoinLinks(sub.inner, links, processed);
            }
        }
        return links;
    }

    static void addJoinLink(LineageSQLParser.ParseResult pr, LineageSQLParser.JoinRel join,
                            List<Link> links, Set<String> processed) {
        String from = throughSubQuery(resolveTableName(join.fromAlias, pr), pr);
        String to = throughSubQuery(resolveTableName(join.toAlias, pr), pr);
        if (from == null || to == null || from.equalsIgnoreCase(to)) return;
        String newTable, existTable;
        if (processed.contains(from.toLowerCase()) && !processed.contains(to.toLowerCase())) {
            existTable = from; newTable = to;
        } else if (processed.contains(to.toLowerCase()) && !processed.contains(from.toLowerCase())) {
            existTable = to; newTable = from;
        } else {
            return;
        }
        processed.add(newTable.toLowerCase());
        String cond = resolveTableName(join.fromAlias, pr) + "." + join.fromColumn
                + " = " + resolveTableName(join.toAlias, pr) + "." + join.toColumn;
        links.add(new Link(newTable, existTable, "REFERENCE", 2, cond));
    }

    static void collectInnerJoinLinks(LineageSQLParser.ParseResult inner, List<Link> links, Set<String> processed) {
        if (inner == null) return;
        if (inner.joins != null) {
            for (LineageSQLParser.JoinRel join : inner.joins) addJoinLink(inner, join, links, processed);
        }
        if (inner.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : inner.subQueries) collectInnerJoinLinks(sub.inner, links, processed);
        }
    }

    static String throughSubQuery(String aliasOrTable, LineageSQLParser.ParseResult pr) {
        if (aliasOrTable == null || pr.subQueries == null) return aliasOrTable;
        for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
            if (aliasOrTable.equalsIgnoreCase(sub.alias) && sub.inner != null && !sub.inner.tables.isEmpty()) {
                return sub.inner.tables.get(0).name;
            }
        }
        return aliasOrTable;
    }

    static String resolveTableName(String aliasOrTable, LineageSQLParser.ParseResult pr) {
        if (pr.tables != null) {
            for (LineageSQLParser.TableRef t : pr.tables) {
                if (aliasOrTable.equalsIgnoreCase(t.alias) || aliasOrTable.equalsIgnoreCase(t.name)) return t.name;
            }
        }
        return aliasOrTable;
    }
}
