package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.MetadataColumn;
import com.dam.catalog.entity.MetadataSchema;
import com.dam.catalog.entity.MetadataTable;
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
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 数据血缘识别服务
 * <p>
 * 基于编码智能体从 SQL 中识别数据血缘（表级 + 字段级分开）：
 * <ul>
 *   <li>检索数据源已同步的表结构（含中文注释），作为「业务语义」上下文喂给 AI</li>
 *   <li>识别原则：业务语义优先于外键字典——优先依据表注释/字段注释判断关联，
 *       _DR 后缀「指针」字段是 IRIS 系统表间隐式关联的重要线索</li>
 *   <li>输出结构化血缘：{tableLineages, columnLineages}，由前端预览确认后调 dam-lineage 落库</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineageAiService {

    /** 单次注入 Prompt 的最大表数量 */
    private static final int MAX_TABLES = 80;

    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataSchemaMapper schemaMapper;

    /** dam-agent 服务地址（血缘识别调用编码智能体） */
    @Value("${dam.agent-url:http://localhost:8089}")
    private String agentUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** agent HTTP 客户端（线程安全，类级单例复用） */
    private static final HttpClient AGENT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * AI 识别 SQL 的数据血缘（表级 + 字段级）
     *
     * @param sql          SQL 语句
     * @param dataSourceId 数据源ID（可选，提供则只检索该数据源的表结构）
     * @param token        当前登录 token（透传给 dam-agent 鉴权）
     * @return {tableLineages, columnLineages, sql}
     */
    public Map<String, Object> recognize(String sql, Long dataSourceId, String token) {
        if (StrUtil.isBlank(sql)) throw new BusinessException("SQL 不能为空");

        // 1. 检索表结构（业务语义上下文）
        String tableContext = buildTableContext(dataSourceId);
        if (StrUtil.isBlank(tableContext)) {
            throw new BusinessException("暂无可参考的表结构，请先在「元数据管理」中同步表后再识别血缘");
        }

        // 2. 从 SQL 反查 -> 指针字段所属表（避免 AI 猜测表名）
        String pointerHint = buildPointerHint(sql, dataSourceId);

        // 3. 组装 Prompt 并调用编码智能体
        String prompt = buildPrompt(sql, tableContext, pointerHint);
        String raw = callAgent(prompt, token);

        // 4. 解析结构化血缘
        Map<String, Object> result = parseLineageJson(raw);
        result.put("sql", sql);
        return result;
    }

    /**
     * AI 识别数据源内各表之间的关联关系（无需 SQL，依据 _DR 指针命名约定 + 字段注释的业务语义）。
     * <p>
     * 用于「数据源同步表后自动识别表间关联」：业务语义优先于外键字典。
     *
     * @param dataSourceId 数据源ID
     * @param token        当前登录 token（透传给 dam-agent 鉴权）
     * @return {tableLineages, columnLineages, dataSourceId}
     */
    public Map<String, Object> recognizeTableRelations(Long dataSourceId, String token) {
        if (dataSourceId == null) throw new BusinessException("请选择数据源");
        String tableContext = buildTableContext(dataSourceId);
        if (StrUtil.isBlank(tableContext)) {
            throw new BusinessException("该数据源暂无已同步的表结构，请先同步表");
        }
        String raw = callAgent(buildTableRelationPrompt(tableContext), token);
        Map<String, Object> result = parseLineageJson(raw);
        result.put("dataSourceId", dataSourceId);
        return result;
    }

    /** 组装表间关联识别 Prompt（无 SQL，依据 _DR 指针 + 注释） */
    private String buildTableRelationPrompt(String tableContext) {
        return "你是医疗行业数据治理专家。请分析以下数据库表结构，识别各表之间的关联关系（数据血缘）。\n\n"
                + "可用表结构：\n" + tableContext + "\n"
                + "要求：\n"
                + "1. 字段名以 _DR 结尾是指针，指向另一张表的主键（RowId）。推断指向哪张表时，用字段名去掉 _DR 后的关键词（如 PAADM_RegConDis_DR 的 RegConDis）在表结构清单里匹配真实表名（如 DHC_RegConDisCount），再结合字段注释确认，不要凭空编造表名\n"
                + "2. 表级血缘方向（主次关系）：sourceTable=被指向的父表（数据来源），targetTable=持有指针的子表（引用方）\n"
                + "3. 字段级血缘方向同理：sourceTable=被指向表，sourceColumn=被引用字段；targetTable=引用表，targetColumn=指针字段\n"
                + "4. 业务语义优先于外键字典（很多表没有物理外键，但业务上有关联）\n"
                + "5. 只输出 JSON：{\"tableLineages\":[{\"sourceTable\":\"被指向表\",\"targetTable\":\"引用表\",\"relationType\":\"REFERENCE\",\"transformRule\":\"关联说明\"}],\"columnLineages\":[{\"sourceTable\":\"被指向表\",\"sourceColumn\":\"被引用字段\",\"targetTable\":\"引用表\",\"targetColumn\":\"指针字段\",\"mappingType\":\"JOIN\",\"transformExpression\":\"\",\"joinCondition\":\"引用表.指针字段 -> 被指向表.主键\"}]}\n"
                + "6. 表名、字段名必须严格使用上面给出的真实名称，不要编造";
    }

    /**
     * 检索数据源下已同步的表结构与字段（含中文注释），作为业务语义上下文
     */
    private String buildTableContext(Long dataSourceId) {
        LambdaQueryWrapper<MetadataTable> tw = new LambdaQueryWrapper<>();
        if (dataSourceId != null) tw.eq(MetadataTable::getDatasourceId, dataSourceId);
        tw.orderByAsc(MetadataTable::getTableName).last("LIMIT " + MAX_TABLES);
        List<MetadataTable> tables = tableMapper.selectList(tw);
        if (tables.isEmpty()) return "";

        // schema 名称经 schema_id 批量解析
        Set<Long> schemaIds = tables.stream().map(MetadataTable::getSchemaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> schemaNameMap = new HashMap<>();
        if (!schemaIds.isEmpty()) {
            for (MetadataSchema s : schemaMapper.selectByIds(schemaIds)) {
                if (s != null) schemaNameMap.put(s.getId(), s.getSchemaName());
            }
        }
        // 字段按 tableId 分组
        Set<Long> tableIds = tables.stream().map(MetadataTable::getId).collect(Collectors.toSet());
        Map<Long, List<MetadataColumn>> colsMap = columnMapper.selectList(
                        new LambdaQueryWrapper<MetadataColumn>()
                                .in(MetadataColumn::getTableId, tableIds)
                                .orderByAsc(MetadataColumn::getOrdinalPosition))
                .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId));

        StringBuilder sb = new StringBuilder();
        for (MetadataTable t : tables) {
            String schema = t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "";
            String fullName = StrUtil.isNotBlank(schema) && !"public".equalsIgnoreCase(schema)
                    ? schema + "." + t.getTableName() : t.getTableName();
            sb.append("=== ").append(fullName);
            if (StrUtil.isNotBlank(t.getTableComment())) sb.append("（").append(t.getTableComment()).append("）");
            sb.append(" ===\n");
            for (MetadataColumn c : colsMap.getOrDefault(t.getId(), Collections.emptyList())) {
                sb.append("- ").append(c.getColumnName())
                        .append(" (").append(StrUtil.isBlank(c.getColumnType()) ? "未知类型" : c.getColumnType()).append(")");
                if (Boolean.TRUE.equals(c.getIsPrimaryKey())) sb.append(" PK");
                if (Boolean.TRUE.equals(c.getIsForeignKey())) sb.append(" FK");
                if (StrUtil.isNotBlank(c.getColumnComment())) sb.append(" # ").append(c.getColumnComment());
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 从 SQL 提取 -> 后面的字段名，反查元数据表结构，生成「字段→表」映射提示（避免 AI 猜测表名；处理同名字段多表歧义） */
    private String buildPointerHint(String sql, Long dataSourceId) {
        Set<String> fieldNames = new TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("->([A-Za-z_][A-Za-z0-9_]*)").matcher(sql);
        while (m.find()) {
            fieldNames.add(m.group(1));
        }
        if (fieldNames.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String field : fieldNames) {
            // 0. 优先读同步时记录的外键引用表（reference_table_name，最可靠）
            Set<String> refTables = columnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                            .eq(MetadataColumn::getColumnName, field)
                            .isNotNull(MetadataColumn::getReferenceTableName)
                            .ne(MetadataColumn::getReferenceTableName, "")
                            .eq(dataSourceId != null, MetadataColumn::getDatasourceId, dataSourceId))
                    .stream().map(MetadataColumn::getReferenceTableName)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (refTables.size() == 1) {
                sb.append("- ").append(field).append(" 字段属于 ").append(refTables.iterator().next()).append(" 表\n");
                continue;
            }
            if (!refTables.isEmpty()) {
                sb.append("- ").append(field).append(" 字段可能属于以下表之一：").append(String.join("、", refTables)).append("\n");
                continue;
            }
            // 1. 精确匹配（限定数据源范围）
            Set<String> tables = columnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                            .eq(MetadataColumn::getColumnName, field)
                            .eq(dataSourceId != null, MetadataColumn::getDatasourceId, dataSourceId))
                    .stream().map(c -> tableNameOf(c.getTableId()))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (tables.size() == 1) {
                sb.append("- ").append(field).append(" 字段属于 ").append(tables.iterator().next()).append(" 表\n");
                continue;
            }
            // 2. 精确匹配不唯一或为空：用字段名前缀（下划线前，如 CTLOC）做前缀匹配
            int idx = field.indexOf('_');
            String prefix = idx > 0 ? field.substring(0, idx) : field;
            Set<String> prefixTables = columnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                            .likeRight(MetadataColumn::getColumnName, prefix + "_")
                            .eq(dataSourceId != null, MetadataColumn::getDatasourceId, dataSourceId))
                    .stream().map(c -> tableNameOf(c.getTableId()))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (prefixTables.size() == 1) {
                sb.append("- ").append(field).append(" 字段属于 ").append(prefixTables.iterator().next()).append(" 表\n");
            } else if (!prefixTables.isEmpty()) {
                sb.append("- ").append(field).append(" 字段可能属于以下表之一（请结合指针字段名的业务语义选择）：")
                        .append(String.join("、", prefixTables)).append("\n");
            }
        }
        return sb.toString();
    }

    /** 按 tableId 解析表名 */
    private String tableNameOf(Long tableId) {
        if (tableId == null) return null;
        MetadataTable t = tableMapper.selectById(tableId);
        return t != null ? t.getTableName() : null;
    }

    /** 组装血缘识别 Prompt（表级主次方向 + IRIS _DR 指针语法 + 业务语义优先） */
    private String buildPrompt(String sql, String tableContext, String pointerHint) {
        return "你是医疗行业数据治理专家，擅长分析数据血缘。请分析下面的 SQL，识别其中的表级血缘和字段级血缘。\n\n"
                + "可用表结构（表名/注释 + 字段名/类型/注释，PK=主键，FK=外键）：\n" + tableContext + "\n"
                + "SQL：\n" + sql + "\n\n"
                + "【指针字段所属表（已从元数据反查，直接使用，禁止另猜表名）】\n"
                + (StrUtil.isNotBlank(pointerHint) ? pointerHint : "（无）") + "\n\n"
                + "【表级血缘方向（主次关系）】\n"
                + "1. JOIN 条件通常写作「子表.外键 = 父表.主键」，数据从父表流向子表\n"
                + "2. 表级血缘方向必须是「父表 → 子表」：sourceTable=父表（数据来源），targetTable=子表（引用方）\n"
                + "3. 示例：JOIN PA_Adm pa ON pa.PAADM_RowID = o.OEORD_Adm_DR 的方向是 PA_Adm → OE_Order\n"
                + "4. 逐层 JOIN 请按关联链顺序输出（如 PA_Adm → OE_Order → OE_OrdItem），不要输出方向相反的散乱边\n\n"
                + "【字段级血缘（IRIS 指针语法）】\n"
                + "5. 「别名.字段_DR->目标字段」是跨表取字段：字段_DR 是「指针」指向另一张表，-> 后取该表的字段\n"
                + "6. 指针指向的表以「指针字段所属表」反查结果为准，不要另猜表名；反查结果里没有或有多张候选的字段，才结合指针字段名的业务语义（去掉 _DR 后的关键词）和字段注释判断\n"
                + "   例1：pa.PAADM_DepCode_DR->CTLOC_Desc → 前缀 CTLOC_ 匹配 CT_Loc 表\n"
                + "   例2：pae.PAADM_RegConDis_DR->RCD_Desc → 前缀 RCD_ 匹配 DHC_RegConDisCount 表（不是 RCD 表）\n"
                + "   例3：pa.PAADM_PAPMI_DR->PAPMI_No → 前缀 PAPMI_ 匹配 PA_PatMas 表\n"
                + "7. 指针指向的表必须是「可用表结构」里真实存在的表名，绝不要凭空编造不存在的表名\n"
                + "8. 多层指针（->A_DR->B_Desc）需完整解析链路，中间表也纳入表级血缘\n"
                + "9. 指针指向的表即使不在 JOIN 中，也要作为表级血缘节点\n\n"
                + "【字段级血缘方向】\n"
                + "10. source=被指针指向的表（数据来源），target=持有指针的表（引用方）\n"
                + "11. 例：{\"sourceTable\":\"CT_Loc\",\"sourceColumn\":\"CTLOC_Desc\",\"targetTable\":\"PA_Adm\",\"targetColumn\":\"PAADM_DepCode_DR\",\"mappingType\":\"JOIN\",\"joinCondition\":\"PA_Adm.PAADM_DepCode_DR -> CT_Loc.CTLOC_RowId\"}\n\n"
                + "【其他】\n"
                + "12. 业务语义优先于外键字典\n"
                + "13. 只输出 JSON：{\"tableLineages\":[{\"sourceTable\":\"父表\",\"targetTable\":\"子表\",\"relationType\":\"FLOW\",\"transformRule\":\"关联说明\"}],\"columnLineages\":[{\"sourceTable\":\"源表\",\"sourceColumn\":\"源字段\",\"targetTable\":\"目标表\",\"targetColumn\":\"目标字段\",\"mappingType\":\"DIRECT\",\"transformExpression\":\"\",\"joinCondition\":\"\"}]}\n"
                + "14. 表名、字段名必须严格使用上面给出的真实名称，不要编造";
    }

    /** 调用 dam-agent 编码智能体，返回其 content */
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
            log.error("调用编码智能体识别血缘失败", e);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }
    }

    /** 解析 AI 输出的结构化血缘 JSON */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLineageJson(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableLineages", List.of());
        result.put("columnLineages", List.of());
        if (StrUtil.isBlank(content)) return result;
        String text = content.trim()
                .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                .replaceAll("(?s)```$", "").trim();
        try {
            JsonNode node = MAPPER.readTree(text);
            JsonNode tl = node.path("tableLineages");
            JsonNode cl = node.path("columnLineages");
            if (tl.isArray()) result.put("tableLineages", MAPPER.convertValue(tl, List.class));
            if (cl.isArray()) result.put("columnLineages", MAPPER.convertValue(cl, List.class));
        } catch (Exception e) {
            log.warn("AI 血缘识别结果解析失败: {}", e.getMessage());
        }
        return result;
    }
}
