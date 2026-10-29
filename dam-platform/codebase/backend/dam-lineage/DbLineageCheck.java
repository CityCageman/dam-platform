import java.sql.*;

/**
 * 临时验证类：直连 PostgreSQL 检查血缘落库现状（验证后删除）
 * 重点：节点是否存在 DHC_INVPRT/DHC_BillConINV（新代码子查询穿透产物）、
 *      报表节点的字段级血缘来源是否已变为取值字段（CTLOC_Desc 等）
 */
public class DbLineageCheck {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://10.162.3.16:11001/damplatform";
        String user = "dam";
        String pwd = "Cuhkszmc_2026";
        try (Connection conn = DriverManager.getConnection(url, user, pwd)) {

            System.out.println("===== 1. 关键表节点（是否存在子查询内部表） =====");
            query(conn, "SELECT id, source_code, source_name, source_type_id, created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_lineage_source " +
                    "WHERE source_code IN ('DHC_INVPRT','DHC_BillConINV','PA_Adm','CT_Loc') ORDER BY id");

            System.out.println("===== 2. REPORT 类型节点（最近10个） =====");
            query(conn, "SELECT id, source_code, source_name, created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_lineage_source WHERE source_type_id='REPORT' " +
                    "ORDER BY id DESC LIMIT 10");

            System.out.println("===== 3. 报表节点的字段级血缘（target=报表，最近创建的报表） =====");
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT id, source_code, source_name FROM dam_metadata.metadata_lineage_source " +
                            "WHERE source_type_id='REPORT' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                long reportId = rs.getLong("id");
                String code = rs.getString("source_code");
                System.out.println("报表节点: id=" + reportId + " code=" + code);
                query(conn, "SELECT cl.id, src.source_code AS src_table, cl.source_column_name, " +
                        "cl.target_column_name, cl.mapping_type_id, cl.transform_function_id, cl.join_condition, " +
                        "cl.is_primary_source, cl.confidence_score " +
                        "FROM dam_metadata.metadata_column_lineage cl " +
                        "LEFT JOIN dam_metadata.metadata_lineage_source src ON cl.source_table_id = src.id " +
                        "WHERE cl.target_table_id = " + reportId + " ORDER BY cl.id");
            }

            System.out.println("===== 4. 报表SQL解析记录（sql_hash 去重命中情况） ====");
            query(conn, "SELECT id, report_name, parse_status_id, created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_report_sql_parser ORDER BY id DESC LIMIT 10");
            
            System.out.println("===== 5. 字段级血缘最近25条（看新代码是否落库） ====");
            query(conn, "SELECT cl.id, src.source_code AS src_table, cl.source_column_name, " +
                    "tgt.source_code AS tgt_table, cl.target_column_name, cl.mapping_type_id, " +
                    "cl.is_primary_source, cl.created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_column_lineage cl " +
                    "LEFT JOIN dam_metadata.metadata_lineage_source src ON cl.source_table_id = src.id " +
                    "LEFT JOIN dam_metadata.metadata_lineage_source tgt ON cl.target_table_id = tgt.id " +
                    "ORDER BY cl.id DESC LIMIT 25");
            
            System.out.println("===== 6. 报表节点(106)相关的分层边 hierarchy ====");
            query(conn, "SELECT h.id, up.source_code AS upstream, up.source_type_id AS up_type, " +
                    "down.source_code AS downstream, down.source_type_id AS down_type, " +
                    "h.created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_lineage_hierarchy h " +
                    "LEFT JOIN dam_metadata.metadata_lineage_source up ON h.upstream_source_id = up.id " +
                    "LEFT JOIN dam_metadata.metadata_lineage_source down ON h.downstream_source_id = down.id " +
                    "WHERE h.upstream_source_id = 106 OR h.downstream_source_id = 106 " +
                    "ORDER BY h.id DESC LIMIT 15");
            
            System.out.println("===== 7. 宏观投影 metadata_lineage（106 相关） ====");
            query(conn, "SELECT id, source_type, source_id, target_type, target_id, relation_type, " +
                    "created_at::timestamp(0) AS created " +
                    "FROM dam_metadata.metadata_lineage " +
                    "WHERE source_id = 106 OR target_id = 106 ORDER BY id DESC LIMIT 15");
        }
    }

    static void query(Connection conn, String sql) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            int n = 0;
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) sb.append(" | ");
                    String v = rs.getString(i);
                    sb.append(md.getColumnLabel(i)).append("=").append(v == null ? "null" : v);
                }
                System.out.println(sb);
                n++;
            }
            System.out.println("  （" + n + " 行）\n");
        } catch (SQLException e) {
            System.out.println("  查询失败: " + e.getMessage());
        }
    }
}
