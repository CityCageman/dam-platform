import com.dam.lineage.service.LineageSQLParser;

/**
 * 临时验证类：用真实"门诊工作量报表"SQL 验证 JOIN 溯源解析（验证后删除）
 */
public class LineageParseVerify {
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

        System.out.println("===== 表 =====");
        for (LineageSQLParser.TableRef t : pr.tables) {
            System.out.println("  " + t.name + " (alias=" + t.alias + ")");
        }

        System.out.println("===== JOIN =====");
        for (LineageSQLParser.JoinRel j : pr.joins) {
            System.out.println("  " + j.fromAlias + "." + j.fromColumn + " = " + j.toAlias + "." + j.toColumn + " [" + j.joinType + "]");
        }

        System.out.println("===== 字段 =====");
        for (LineageSQLParser.ColumnRef c : pr.columns) {
            System.out.println("  " + c.alias + " | 直接来源=" + c.sourceTable + "." + c.sourceColumn
                    + " | JOIN真源=" + (c.joinSourceTable != null ? c.joinSourceTable + "." + c.joinSourceColumn : "-")
                    + " | 本表JOIN键=" + (c.joinKeyColumn != null ? c.joinKeyColumn : "-")
                    + " | 关联条件=" + (c.joinCondition != null ? c.joinCondition : "-"));
        }
        System.out.println("===== 子查询 =====");
        for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
            System.out.println("  alias=" + sub.alias);
            for (LineageSQLParser.TableRef t : sub.inner.tables) {
                System.out.println("    inner表: " + t.name + " (alias=" + t.alias + ")");
            }
            for (LineageSQLParser.ColumnRef c : sub.inner.columns) {
                System.out.println("    inner字段: " + c.alias + " | 来源=" + c.sourceTable + "." + c.sourceColumn
                        + " | 类型=" + c.mappingType
                        + " | JOIN真源=" + (c.joinSourceTable != null ? c.joinSourceTable + "." + c.joinSourceColumn : "-"));
            }
        }
    }
}
