package com.dam.lineage.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.vo.PageResult;
import com.dam.lineage.entity.*;
import com.dam.lineage.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 血缘精细落库服务
 * <p>
 * 负责"宏观索引 + 微观详情"双层血缘的完整落库链路：
 * <pre>
 *  报表SQL → report_sql_parser（原文+解析结果）
 *          → lineage_source（节点自动注册，与资产解耦）
 *          → lineage_hierarchy（分层精细边）
 *          → metadata_lineage（宏观投影，供现有图谱/影响分析快速查询）
 *          → column_lineage（字段级血缘）
 *          → column_calc_dependency（计算字段依赖）
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineagePersistService {

    /** 节点类型常量 */
    private static final String NODE_TYPE_REPORT = "REPORT";
    private static final String NODE_TYPE_TABLE = "TABLE";

    /** 发现方式 */
    private static final String DISCOVERED_BY = "PARSER";

    private final LineageSourceService sourceService;
    private final MetadataLineageHierarchyMapper hierarchyMapper;
    private final MetadataColumnLineageMapper columnLineageMapper;
    private final MetadataColumnCalcDependencyMapper calcDependencyMapper;
    private final MetadataReportSqlParserMapper reportParserMapper;
    private final DamLineageMapper lineageMapper;
    private final LineageSQLParser sqlParser;

    /**
     * 保存报表/视图 SQL 并完成全链路血缘落库
     *
     * @param reportCode   报表/视图节点编码（唯一，如报表编码/视图名）
     * @param reportName   报表名称
     * @param reportType   报表类型（report_type 字典：REPORT/VIEW/PROCEDURE/ETL_JOB/ADHOC）
     * @param sql          原始 SQL
     * @param sourceSystem 来源系统标识
     * @param categoryId   资产分类ID（报表节点归入分类，可空）
     * @param collectionId 采集记录ID（可空）
     * @param replace      是否全量替换（true：先清空该报表旧血缘再重新解析，用于 SQL 更新）
     * @return 落库摘要（parserId/新增节点数/血缘边数/字段映射数/计算依赖数）
     */
    @Transactional
    public Map<String, Object> saveAndParseReport(String reportCode, String reportName, String reportType,
                                                  String sql, String sourceSystem, Long categoryId,
                                                  Long collectionId, boolean replace) {
        if (StrUtil.isBlank(sql)) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        String type = StrUtil.isNotBlank(reportType) ? reportType : NODE_TYPE_REPORT;

        // 1. 报表节点自动注册（存在复用；首次创建时归入所选分类）
        MetadataLineageSource reportNode = sourceService.upsert(
                reportCode, reportName, type, null, sql, null, null, categoryId, "血缘节点（SQL 采集）");
        summary.put("reportSourceId", reportNode.getId());

        // 2. 全量替换：先清空该报表节点的旧血缘（hierarchy/column_lineage/calc_dependency/宏观投影/旧解析记录）
        if (replace) {
            clearReportLineage(reportNode.getId());
            summary.put("replaced", true);
        }

        // 3. SQL 哈希去重（仅非替换模式；同一 SQL 只解析一次）
        String hash = sha256(sql);
        if (!replace) {
            MetadataReportSqlParser existing = reportParserMapper.selectOne(
                    new LambdaQueryWrapper<MetadataReportSqlParser>().eq(MetadataReportSqlParser::getSqlHash, hash)
                            .eq(MetadataReportSqlParser::getParseStatusId, "SUCCESS")
                            .last("LIMIT 1"));
            if (existing != null) {
                summary.put("parserId", existing.getId());
                summary.put("duplicate", true);
                summary.put("message", "SQL 已解析过，跳过重复解析");
                return summary;
            }
        }

        // 4. 结构化解析
        LineageSQLParser.ParseResult pr = sqlParser.parseReportSql(sql);

        // 4. 落库 SQL 解析结果
        MetadataReportSqlParser parser = new MetadataReportSqlParser();
        parser.setReportSourceId(reportNode.getId());
        parser.setReportName(StrUtil.isNotBlank(reportName) ? reportName : reportCode);
        parser.setReportTypeId(type);
        parser.setSqlContent(sql);
        parser.setSqlHash(hash);
        parser.setParsedTables(toJson(pr.tables));
        parser.setParsedColumns(toJson(pr.columns));
        parser.setJoinRelations(toJson(pr.joins));
        parser.setFilterConditions(toJson(pr.filters));
        parser.setGroupByFields(toJson(pr.groupBy));
        parser.setOrderByFields(toJson(pr.orderBy));
        parser.setCalculatedFields(toJson(pr.columns.stream().filter(c -> !"DIRECT".equals(c.mappingType)).toList()));
        parser.setParseStatusId("SUCCESS");
        parser.setParseTime(LocalDateTime.now());
        parser.setParserVersion("1.0");
        parser.setCollectionId(collectionId);
        parser.setIsAutoUpdated(false);
        fillAudit(parser);
        reportParserMapper.insert(parser);
        summary.put("parserId", parser.getId());
        summary.put("tablesParsed", pr.tables.size());
        summary.put("columnsParsed", pr.columns.size());

        // 5. 表节点自动注册（含主查询与子查询内部全部表）
        int nodeCount = 0, hierarchyCount = 0, columnCount = 0, calcCount = 0;
        // 已注册的表节点缓存（别名→节点）
        Map<String, MetadataLineageSource> tableNodeCache = new HashMap<>();
        // 递归收集主查询与子查询内部全部表引用（子查询穿透落库依赖内部表节点，如 ia 内部的 DHC_INVPRT）
        List<LineageSQLParser.TableRef> allTables = new ArrayList<>();
        collectAllTables(pr, allTables);
        Set<String> registeredTables = new HashSet<>();
        for (LineageSQLParser.TableRef tableRef : allTables) {
            // 同表（含子查询内重复引用）只注册一次节点
            if (!registeredTables.add(tableRef.name)) continue;
            MetadataLineageSource tableNode = sourceService.upsert(
                    tableRef.name, tableRef.name, NODE_TYPE_TABLE, null, null, null, null, null, "SQL 解析自动注册");
            tableNodeCache.put(tableRef.name, tableNode);
            tableNodeCache.put(tableRef.alias, tableNode);
            nodeCount++;
        }

        // 5.1 表级血缘传导链路：主表→报表（应用层 DEPEND），JOIN 引入表→其关联表（物理层 REFERENCE，携带关联条件），
        //     子查询占位别名穿透到内部主表（如 ia → DHC_BillConINV），内部 JOIN 递归接入
        //     例：门诊工作量报表 ← PA_Adm ← CT_Loc / PA_PatMas / CT_CareProv / DHC_BillConINV ← DHC_INVPRT
        //     依赖深度：距消费端(报表)的跳数随链路递增，主表=1，JOIN/子查询引入表逐级+1（如报表←事实表depth=1←字典表depth=2）
        for (TableLink link : buildTableLinks(pr)) {
            MetadataLineageSource upNode = tableNodeCache.get(link.source());
            MetadataLineageSource downNode = "REPORT".equals(link.target()) ? reportNode : tableNodeCache.get(link.target());
            if (upNode == null || downNode == null) continue;
            String downType = "REPORT".equals(link.target()) ? type : downNode.getSourceTypeId();
            String downCode = "REPORT".equals(link.target()) ? reportCode : link.target();

            // 分层精细边（upstream=来源，downstream=目标）
            MetadataLineageHierarchy hierarchy = new MetadataLineageHierarchy();
            hierarchy.setUpstreamSourceId(upNode.getId());
            hierarchy.setUpstreamTypeId(upNode.getSourceTypeId());
            hierarchy.setDownstreamSourceId(downNode.getId());
            hierarchy.setDownstreamTypeId(downType);
            hierarchy.setLayerLevel(link.layerLevel());
            hierarchy.setRelationTypeId(link.relationType());
            hierarchy.setDependencyDepth(link.depth());
            hierarchy.setIsDirectDependency(true);
            hierarchy.setTransformRule(link.transformRule());
            hierarchy.setTransformTypeId("DIRECT");
            hierarchy.setDiscoveredById(DISCOVERED_BY);
            hierarchy.setCollectionId(collectionId);
            hierarchy.setSourceSystemId(reportNode.getSourceSystemId());
            hierarchy.setLineagePath(buildLineagePath(link.source(), downCode));
            fillAudit(hierarchy);
            hierarchyMapper.insert(hierarchy);
            hierarchyCount++;

            // 宏观投影：metadata_lineage（供现有图谱/影响分析快速查询，链路按传导方向落边）
            DamLineage projection = new DamLineage();
            projection.setSourceType(upNode.getSourceTypeId());
            projection.setSourceId(upNode.getId());
            projection.setTargetType(downType);
            projection.setTargetId(downNode.getId());
            projection.setRelationType("FLOW");
            projection.setTransformRule(link.transformRule());
            // 依赖层级=链路深度（距消费端的跳数），与 hierarchy.dependency_depth 保持一致，便于影响分析按深度过滤
            projection.setDependencyLevel(link.depth());
            projection.setSourceSystem(StrUtil.isNotBlank(sourceSystem) ? sourceSystem : "UNKNOWN");
            projection.setCollectionId(collectionId != null ? collectionId : 0L);
            projection.setDiscoveredBy(DISCOVERED_BY);
            fillLineageAudit(projection);
            lineageMapper.insert(projection);
        }

        // 6. 字段级血缘 + 计算依赖
        //    模型：报表输出字段 ← SELECT 直接引用的真实取值来源字段（值来源主边，isPrimarySource=true）
        //          来源字段所在表经 JOIN 引入时，补充"行定位边"（本表 JOIN 键 → JOIN 真源字段，JOIN）
        //          子查询占位别名（如 ia）穿透递归解析到子查询内部真实表/字段，转换语义（SUM 等）随主边保留
        Set<String> joinEdgeKeys = new HashSet<>();
        for (LineageSQLParser.ColumnRef col : pr.columns) {
            if (col.sourceTable == null || col.sourceColumn == null) continue;
            // 穿透子查询：sourceTable 为占位别名时，到 subQueries 中定位同名列定义，逐层下沉到真实表字段
            //    例：ia.总金额 → 子查询输出"总金额" → 真源 DHC_INVPRT.PRT_Acount（SUM(COALESCE(...))）
            LineageSQLParser.ColumnRef resolved = col;
            boolean viaSubQuery = false;
            while (isSubQueryAlias(pr, resolved.sourceTable)) {
                LineageSQLParser.ColumnRef inner = findColumnInSubQuery(pr, resolved.sourceTable, resolved.sourceColumn);
                if (inner == null) break;
                resolved = inner;
                viaSubQuery = true;
            }
            MetadataLineageSource srcNode = tableNodeCache.get(resolved.sourceTable);
            if (srcNode == null) continue;
            // 目标节点即报表/视图节点（字段血缘目标为报表输出字段）
            MetadataLineageSource tgtNode = reportNode;

            // 子查询穿透后，映射类型/转换/依赖取子查询内部字段定义（如 SUM(COALESCE(prt.PRT_Acount,0))）
            String mappingType = viaSubQuery ? resolved.mappingType : col.mappingType;
            String calcType = viaSubQuery ? resolved.calcType : col.calcType;
            String transformExpr = viaSubQuery ? resolved.expression : col.expression;
            String transformFn = viaSubQuery ? resolved.transformFunction : col.transformFunction;
            List<String> dependsOn = viaSubQuery && resolved.dependsOn != null ? resolved.dependsOn : col.dependsOn;
            // 关联条件：内层 JOIN 溯源 + 外层子查询 JOIN 定位
            String joinCond = buildEffectiveJoinCondition(pr, col, resolved, viaSubQuery);

            // A. 主边：报表输出字段 ← 真实取值来源字段（值来源）
            MetadataColumnLineage cl = new MetadataColumnLineage();
            // 报表场景无 metadata_table/metadata_column 记录，source/target 节点即 lineage_source.id，列ID用 0 兜底
            cl.setHierarchyId(hierarchyCount > 0 ? firstHierarchyId(pr, tableNodeCache, reportNode, srcNode, sql) : 0L);
            cl.setSourceTableId(srcNode.getId());
            cl.setSourceColumnId(0L);
            cl.setSourceColumnName(resolved.sourceColumn);
            cl.setTargetTableId(tgtNode.getId());
            cl.setTargetColumnId(0L);
            cl.setTargetColumnName(col.alias);
            cl.setMappingTypeId(mappingType);
            cl.setTransformExpression(transformExpr);
            cl.setTransformFunctionId(transformFn);
            cl.setJoinCondition(joinCond);
            cl.setFilterCondition(pr.filters.isEmpty() ? null : String.join(" AND ", pr.filters));
            cl.setAggregationTypeId("AGGREGATE".equals(mappingType) ? extractAggregation(transformFn) : null);
            cl.setIsPrimarySource(true);
            cl.setConfidenceScore(new java.math.BigDecimal("1.00"));
            cl.setCollectionId(collectionId);
            cl.setDiscoveredById(DISCOVERED_BY);
            fillAudit(cl);
            columnLineageMapper.insert(cl);
            // 主字段血缘ID（计算依赖挂载目标）
            Long primaryLineageId = cl.getId();
            columnCount++;

            // B. JOIN 行定位边：来源字段所在表由 JOIN 引入时，记录本表侧 JOIN 键 → JOIN 真源字段
            //    例：就诊科室值来源 CT_Loc.CTLOC_Desc；定位边 CT_Loc.CTLOC_RowId → PA_Adm.PAADM_DepCode_DR
            //    防御：同表自关联且字段相同不生成；同一对字段只生成一条（多字段共享 JOIN 键时去重）
            if (StrUtil.isNotBlank(resolved.joinSourceTable) && StrUtil.isNotBlank(resolved.joinSourceColumn)
                    && StrUtil.isNotBlank(resolved.joinKeyColumn)) {
                MetadataLineageSource joinSrcNode = tableNodeCache.get(resolved.joinSourceTable);
                if (joinSrcNode != null && !(joinSrcNode.getId().equals(srcNode.getId())
                        && resolved.joinSourceColumn.equalsIgnoreCase(resolved.joinKeyColumn))) {
                    String edgeKey = srcNode.getId() + "|" + resolved.joinKeyColumn + "|"
                            + joinSrcNode.getId() + "|" + resolved.joinSourceColumn;
                    if (joinEdgeKeys.add(edgeKey)) {
                        MetadataColumnLineage rcl = new MetadataColumnLineage();
                        rcl.setHierarchyId(0L);
                        rcl.setSourceTableId(srcNode.getId());
                        rcl.setSourceColumnId(0L);
                        rcl.setSourceColumnName(resolved.joinKeyColumn);
                        rcl.setTargetTableId(joinSrcNode.getId());
                        rcl.setTargetColumnId(0L);
                        rcl.setTargetColumnName(resolved.joinSourceColumn);
                        rcl.setMappingTypeId("JOIN");
                        rcl.setJoinCondition(joinCond);
                        rcl.setTransformExpression(null);
                        rcl.setIsPrimarySource(false);
                        rcl.setConfidenceScore(new java.math.BigDecimal("0.90"));
                        rcl.setCollectionId(collectionId);
                        rcl.setDiscoveredById(DISCOVERED_BY);
                        fillAudit(rcl);
                        columnLineageMapper.insert(rcl);
                        columnCount++;
                    }
                }
            }

            // C. 计算字段依赖（CALC/AGGREGATE/FUNCTION/CASE 类型）
            if (!"DIRECT".equals(mappingType)) {
                MetadataColumnCalcDependency calc = new MetadataColumnCalcDependency();
                calc.setTargetColumnLineageId(primaryLineageId);
                calc.setTargetColumnId(0L);
                calc.setTargetColumnName(col.alias);
                calc.setCalcTypeId(calcType);
                calc.setCalcExpression(transformExpr);
                // 依赖字段：从 dependsOn 解析为 字段名列表
                List<String> depNames = new ArrayList<>();
                List<Long> depIds = new ArrayList<>();
                if (dependsOn != null) {
                    for (String dep : dependsOn) {
                        int dot = dep.indexOf('.');
                        String field = dot >= 0 ? dep.substring(dot + 1) : dep;
                        depNames.add(field);
                        depIds.add(0L);
                    }
                }
                calc.setDependsOnColumnNames(depNames);
                calc.setDependsOnColumnIds(depIds);
                calc.setDependencyOperatorId(extractOperator(transformExpr));
                calc.setCalcOrder(1);
                calc.setIsCascading(false);
                fillAudit(calc);
                calcDependencyMapper.insert(calc);
                calcCount++;
            }
        }

        summary.put("nodesCreated", nodeCount);
        summary.put("hierarchyEdges", hierarchyCount);
        summary.put("columnLineage", columnCount);
        summary.put("calcDependencies", calcCount);
        return summary;
    }

    // ==================== 辅助方法 ====================

    /**
     * 清空指定报表节点的全部旧血缘（用于全量替换）
     * <p>
     * 级联清理顺序（依赖倒序）：
     * calc_dependency → column_lineage → hierarchy → metadata_lineage 宏观投影 → report_sql_parser
     *
     * @param reportSourceId 报表/视图节点ID（metadata_lineage_source.id）
     */
    @Transactional
    public void clearReportLineage(Long reportSourceId) {
        if (reportSourceId == null) return;
        // 1. 该报表作为源或目标的字段级血缘
        List<MetadataColumnLineage> cls = columnLineageMapper.selectList(
                new LambdaQueryWrapper<MetadataColumnLineage>()
                        .eq(MetadataColumnLineage::getSourceTableId, reportSourceId)
                        .or().eq(MetadataColumnLineage::getTargetTableId, reportSourceId));
        if (!cls.isEmpty()) {
            List<Long> clIds = cls.stream().map(MetadataColumnLineage::getId).toList();
            // 2. 先删计算依赖（挂 column_lineage_id）
            calcDependencyMapper.delete(new LambdaQueryWrapper<MetadataColumnCalcDependency>()
                    .in(MetadataColumnCalcDependency::getTargetColumnLineageId, clIds));
            // 3. 再删字段级血缘
            columnLineageMapper.delete(new LambdaQueryWrapper<MetadataColumnLineage>()
                    .in(MetadataColumnLineage::getId, clIds));
        }
        // 4. 删分层边（报表为上游或下游）
        hierarchyMapper.delete(new LambdaQueryWrapper<MetadataLineageHierarchy>()
                .eq(MetadataLineageHierarchy::getUpstreamSourceId, reportSourceId)
                .or().eq(MetadataLineageHierarchy::getDownstreamSourceId, reportSourceId));
        // 5. 删宏观投影（metadata_lineage，报表为源或目标）
        lineageMapper.delete(new LambdaQueryWrapper<DamLineage>()
                .eq(DamLineage::getSourceId, reportSourceId)
                .or().eq(DamLineage::getTargetId, reportSourceId));
        // 6. 删旧的 SQL 解析记录
        reportParserMapper.delete(new LambdaQueryWrapper<MetadataReportSqlParser>()
                .eq(MetadataReportSqlParser::getReportSourceId, reportSourceId));
    }

    /**
     * 手动创建字段级血缘（如业务表外键引用主数据表）
     * 自动生成：分层边（hierarchy）→ 字段级血缘（column_lineage）→ 宏观投影（metadata_lineage）
     *
     * @param sourceId           源节点ID（引用方，如就诊表节点）
     * @param sourceColumn       源字段名（如 doctor_id）
     * @param targetId           目标节点ID（被引用方，如医护人员表节点）
     * @param targetColumn       目标字段名（如 id）
     * @param mappingType        映射类型（lineage_mapping_type 字典，默认 JOIN）
     * @param joinCondition      关联条件（如 outpatient_visit.doctor_id = doctor.id）
     * @param transformExpression 转换表达式（可空）
     * @param relationType       关系类型（relation_type 字典，默认 REFERENCE）
     * @return 创建的字段级血缘
     */
    @Transactional
    public MetadataColumnLineage createManualColumnLineage(Long sourceId, String sourceColumn,
                                                           Long targetId, String targetColumn,
                                                           String mappingType, String joinCondition,
                                                           String transformExpression, String relationType) {
        MetadataLineageSource src = sourceService.getById(sourceId);
        MetadataLineageSource tgt = sourceService.getById(targetId);
        if (src == null || tgt == null) {
            throw new IllegalArgumentException("源/目标节点不存在");
        }
        String mapType = StrUtil.isNotBlank(mappingType) ? mappingType : "JOIN";
        String relType = StrUtil.isNotBlank(relationType) ? relationType : "REFERENCE";
        String cond = StrUtil.isNotBlank(joinCondition) ? joinCondition : src.getSourceCode() + "." + sourceColumn + " = " + tgt.getSourceCode() + "." + targetColumn;

        // 1. 分层边：上游=目标（被引用/来源），下游=源（引用方）
        MetadataLineageHierarchy h = new MetadataLineageHierarchy();
        h.setUpstreamSourceId(targetId);
        h.setUpstreamTypeId(tgt.getSourceTypeId());
        h.setDownstreamSourceId(sourceId);
        h.setDownstreamTypeId(src.getSourceTypeId());
        h.setLayerLevel(3); // 物理层
        h.setRelationTypeId(relType);
        h.setDependencyDepth(1);
        h.setIsDirectDependency(true);
        h.setTransformRule(cond);
        h.setTransformTypeId("DIRECT");
        h.setDiscoveredById("MANUAL");
        fillAudit(h);
        hierarchyMapper.insert(h);

        // 2. 字段级血缘
        MetadataColumnLineage cl = new MetadataColumnLineage();
        cl.setHierarchyId(h.getId());
        cl.setSourceTableId(targetId);
        cl.setSourceColumnId(0L);
        cl.setSourceColumnName(targetColumn);
        cl.setTargetTableId(sourceId);
        cl.setTargetColumnId(0L);
        cl.setTargetColumnName(sourceColumn);
        cl.setMappingTypeId(mapType);
        cl.setTransformExpression(transformExpression);
        cl.setJoinCondition(cond);
        cl.setIsPrimarySource(false);
        cl.setConfidenceScore(new java.math.BigDecimal("1.00"));
        cl.setDiscoveredById("MANUAL");
        fillAudit(cl);
        columnLineageMapper.insert(cl);

        // 3. 宏观投影
        DamLineage proj = new DamLineage();
        proj.setSourceType(tgt.getSourceTypeId());
        proj.setSourceId(targetId);
        proj.setTargetType(src.getSourceTypeId());
        proj.setTargetId(sourceId);
        proj.setRelationType(relType);
        proj.setTransformRule(cond);
        proj.setDependencyLevel(1);
        proj.setSourceSystem("MANUAL");
        proj.setCollectionId(0L);
        proj.setDiscoveredBy("MANUAL");
        fillLineageAudit(proj);
        lineageMapper.insert(proj);

        return cl;
    }

    /**
     * 批量落库外键引用血缘（扫描自动检测结果）
     * 每个外键：业务表.fromColumn 引用 主数据表.toColumn
     * 生成：节点upsert → 分层边（upstream=主数据表，downstream=业务表，REFERENCE）→
     *       字段级血缘（source=主数据表.toColumn，target=业务表.fromColumn）→ 宏观投影。幂等去重。
     *
     * @param foreignKeys  外键列表 [{fromTable, fromColumn, toTable, toColumn, fkName}]
     * @param sourceSystem 来源系统
     * @param collectionId 采集记录ID（可空）
     * @return 落库摘要 {created, skipped}
     */
    @Transactional
    public Map<String, Object> saveForeignKeyLineage(List<Map<String, Object>> foreignKeys, String sourceSystem, Long collectionId) {
        int created = 0, skipped = 0;
        if (foreignKeys != null) {
            for (Map<String, Object> fk : foreignKeys) {
                String fromTable = str(fk.get("fromTable"));
                String fromColumn = str(fk.get("fromColumn"));
                String toTable = str(fk.get("toTable"));
                String toColumn = str(fk.get("toColumn"));
                if (StrUtil.isBlank(fromTable) || StrUtil.isBlank(fromColumn)
                        || StrUtil.isBlank(toTable) || StrUtil.isBlank(toColumn)) {
                    continue;
                }
                // 1. 节点 upsert（业务表=引用方，主数据表=被引用方）
                MetadataLineageSource fromNode = sourceService.upsert(
                        fromTable, fromTable, NODE_TYPE_TABLE, null, null, null, null, null, "外键引用自动注册");
                MetadataLineageSource toNode = sourceService.upsert(
                        toTable, toTable, NODE_TYPE_TABLE, null, null, null, null, null, "外键引用自动注册");

                // 2. 幂等：已存在相同引用（源表.字段 → 目标表.字段）则跳过
                Long exists = columnLineageMapper.selectCount(
                        new LambdaQueryWrapper<MetadataColumnLineage>()
                                .eq(MetadataColumnLineage::getSourceTableId, toNode.getId())
                                .eq(MetadataColumnLineage::getSourceColumnName, toColumn)
                                .eq(MetadataColumnLineage::getTargetTableId, fromNode.getId())
                                .eq(MetadataColumnLineage::getTargetColumnName, fromColumn));
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }

                String cond = fromTable + "." + fromColumn + " = " + toTable + "." + toColumn;

                // 3. 分层边
                MetadataLineageHierarchy h = new MetadataLineageHierarchy();
                h.setUpstreamSourceId(toNode.getId());
                h.setUpstreamTypeId(NODE_TYPE_TABLE);
                h.setDownstreamSourceId(fromNode.getId());
                h.setDownstreamTypeId(NODE_TYPE_TABLE);
                h.setLayerLevel(3); // 物理层
                h.setRelationTypeId("REFERENCE");
                h.setDependencyDepth(1);
                h.setIsDirectDependency(true);
                h.setTransformRule(cond);
                h.setTransformTypeId("DIRECT");
                h.setDiscoveredById(DISCOVERED_BY);
                h.setCollectionId(collectionId);
                fillAudit(h);
                hierarchyMapper.insert(h);

                // 4. 字段级血缘
                MetadataColumnLineage cl = new MetadataColumnLineage();
                cl.setHierarchyId(h.getId());
                cl.setSourceTableId(toNode.getId());
                cl.setSourceColumnId(0L);
                cl.setSourceColumnName(toColumn);
                cl.setTargetTableId(fromNode.getId());
                cl.setTargetColumnId(0L);
                cl.setTargetColumnName(fromColumn);
                cl.setMappingTypeId("JOIN");
                cl.setJoinCondition(cond);
                cl.setIsPrimarySource(false);
                cl.setConfidenceScore(new java.math.BigDecimal("0.90"));
                cl.setCollectionId(collectionId);
                cl.setDiscoveredById(DISCOVERED_BY);
                fillAudit(cl);
                columnLineageMapper.insert(cl);

                // 5. 宏观投影
                DamLineage proj = new DamLineage();
                proj.setSourceType(NODE_TYPE_TABLE);
                proj.setSourceId(toNode.getId());
                proj.setTargetType(NODE_TYPE_TABLE);
                proj.setTargetId(fromNode.getId());
                proj.setRelationType("REFERENCE");
                proj.setTransformRule(cond);
                proj.setDependencyLevel(1);
                proj.setSourceSystem(StrUtil.isNotBlank(sourceSystem) ? sourceSystem : "UNKNOWN");
                proj.setCollectionId(collectionId != null ? collectionId : 0L);
                proj.setDiscoveredBy(DISCOVERED_BY);
                fillLineageAudit(proj);
                lineageMapper.insert(proj);

                created++;
            }
        }
        return Map.of("created", created, "skipped", skipped);
    }

    /** Map 值安全转字符串 */
    private String str(Object obj) {
        return obj == null ? null : obj.toString();
    }

    /**
     * 批量落库 AI 识别的数据血缘（表级 + 字段级分开）。
     * <p>
     * 表级：sourceTable → targetTable（宏观投影 metadata_lineage）；
     * 字段级：sourceTable.sourceColumn → targetTable.targetColumn（metadata_column_lineage，平铺不挂分层边）。
     * 均为幂等落库：节点按表名 upsert，已存在相同关系则跳过。
     *
     * @param tableLineages  表级血缘 [{sourceTable,targetTable,relationType,transformRule}]
     * @param columnLineages 字段级血缘 [{sourceTable,sourceColumn,targetTable,targetColumn,mappingType,transformExpression,joinCondition}]
     * @param sourceSystem   来源系统
     * @param collectionId   采集记录ID（可空）
     * @return 落库摘要 {tableCreated,columnCreated,skipped}
     */
    @Transactional
    public Map<String, Object> saveAiLineage(List<Map<String, Object>> tableLineages,
                                             List<Map<String, Object>> columnLineages,
                                             String sourceSystem, Long collectionId) {
        return persistLineage(tableLineages, columnLineages, sourceSystem, collectionId, "AI");
    }

    /**
     * 数据中台血缘上报（ingest）：按 taskId 覆盖式落库。
     * 同一 taskId 重复上报时，先逻辑删除该 taskId 的旧血缘，再写入新血缘（避免脏数据残留）。
     *
     * @param taskId         中台任务标识（如 dws_就诊统计），用于覆盖式更新
     * @param sourceSystem   来源系统（如 DATA_PLATFORM）
     * @param tableLineages  表级血缘 [{sourceTable,targetTable,relationType,transformRule}]
     * @param columnLineages 字段级血缘 [{sourceTable,sourceColumn,targetTable,targetColumn,mappingType,transformExpression,joinCondition}]
     * @return 落库摘要 {tableCreated,columnCreated,skipped}
     */
    @Transactional
    public Map<String, Object> ingestLineage(String taskId, String sourceSystem,
                                             List<Map<String, Object>> tableLineages,
                                             List<Map<String, Object>> columnLineages) {
        if (StrUtil.isBlank(taskId)) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        String discoveredBy = "DATA_PLATFORM:" + taskId;
        // 覆盖式更新：先逻辑删除该 taskId 的旧血缘
        lineageMapper.delete(new LambdaQueryWrapper<DamLineage>()
                .eq(DamLineage::getDiscoveredBy, discoveredBy));
        columnLineageMapper.delete(new LambdaQueryWrapper<MetadataColumnLineage>()
                .eq(MetadataColumnLineage::getDiscoveredById, discoveredBy));
        return persistLineage(tableLineages, columnLineages, sourceSystem, null, discoveredBy);
    }

    /** 表级 + 字段级血缘内部落库（节点按表名 upsert，幂等去重） */
    private Map<String, Object> persistLineage(List<Map<String, Object>> tableLineages,
                                               List<Map<String, Object>> columnLineages,
                                               String sourceSystem, Long collectionId, String discoveredBy) {
        int tableCreated = 0, columnCreated = 0, skipped = 0;
        String sys = StrUtil.isNotBlank(sourceSystem) ? sourceSystem : "DATA_PLATFORM";
        Long colId = collectionId != null ? collectionId : 0L;
        Map<String, MetadataLineageSource> nodeCache = new HashMap<>();

        // 1. 表级血缘（宏观投影）
        if (tableLineages != null) {
            for (Map<String, Object> tl : tableLineages) {
                String src = str(tl.get("sourceTable"));
                String tgt = str(tl.get("targetTable"));
                if (StrUtil.isBlank(src) || StrUtil.isBlank(tgt) || src.equalsIgnoreCase(tgt)) continue;
                MetadataLineageSource srcNode = upsertAiNode(src, nodeCache);
                MetadataLineageSource tgtNode = upsertAiNode(tgt, nodeCache);
                String relType = StrUtil.isNotBlank(str(tl.get("relationType"))) ? str(tl.get("relationType")) : "FLOW";
                Long exists = lineageMapper.selectCount(new LambdaQueryWrapper<DamLineage>()
                        .eq(DamLineage::getSourceId, srcNode.getId())
                        .eq(DamLineage::getTargetId, tgtNode.getId())
                        .eq(DamLineage::getRelationType, relType));
                if (exists != null && exists > 0) { skipped++; continue; }
                DamLineage proj = new DamLineage();
                proj.setSourceType(NODE_TYPE_TABLE);
                proj.setSourceId(srcNode.getId());
                proj.setTargetType(NODE_TYPE_TABLE);
                proj.setTargetId(tgtNode.getId());
                proj.setRelationType(relType);
                String rule = str(tl.get("transformRule"));
                proj.setTransformRule(StrUtil.isNotBlank(rule) ? rule : (src + " -> " + tgt));
                proj.setDependencyLevel(1);
                proj.setSourceSystem(sys);
                proj.setCollectionId(colId);
                proj.setDiscoveredBy(discoveredBy);
                fillLineageAudit(proj);
                lineageMapper.insert(proj);
                tableCreated++;
            }
        }

        // 2. 字段级血缘（平铺，不挂分层边）
        if (columnLineages != null) {
            for (Map<String, Object> cl : columnLineages) {
                String srcTable = str(cl.get("sourceTable"));
                String srcCol = str(cl.get("sourceColumn"));
                String tgtTable = str(cl.get("targetTable"));
                String tgtCol = str(cl.get("targetColumn"));
                if (StrUtil.isBlank(srcTable) || StrUtil.isBlank(srcCol)
                        || StrUtil.isBlank(tgtTable) || StrUtil.isBlank(tgtCol)) continue;
                MetadataLineageSource srcNode = upsertAiNode(srcTable, nodeCache);
                MetadataLineageSource tgtNode = upsertAiNode(tgtTable, nodeCache);
                String mapType = StrUtil.isNotBlank(str(cl.get("mappingType"))) ? str(cl.get("mappingType")) : "DIRECT";
                Long exists = columnLineageMapper.selectCount(new LambdaQueryWrapper<MetadataColumnLineage>()
                        .eq(MetadataColumnLineage::getSourceTableId, srcNode.getId())
                        .eq(MetadataColumnLineage::getSourceColumnName, srcCol)
                        .eq(MetadataColumnLineage::getTargetTableId, tgtNode.getId())
                        .eq(MetadataColumnLineage::getTargetColumnName, tgtCol));
                if (exists != null && exists > 0) { skipped++; continue; }
                MetadataColumnLineage col = new MetadataColumnLineage();
                col.setSourceTableId(srcNode.getId());
                col.setSourceColumnId(0L);
                col.setSourceColumnName(srcCol);
                col.setTargetTableId(tgtNode.getId());
                col.setTargetColumnId(0L);
                col.setTargetColumnName(tgtCol);
                col.setMappingTypeId(mapType);
                col.setTransformExpression(str(cl.get("transformExpression")));
                col.setJoinCondition(str(cl.get("joinCondition")));
                col.setIsPrimarySource(true);
                col.setConfidenceScore(new java.math.BigDecimal("0.80"));
                col.setCollectionId(colId);
                col.setDiscoveredById(discoveredBy);
                fillAudit(col);
                columnLineageMapper.insert(col);
                columnCreated++;
            }
        }
        return Map.of("tableCreated", tableCreated, "columnCreated", columnCreated, "skipped", skipped);
    }

    /** AI 血缘落库：按表名缓存 upsert 血缘节点 */
    private MetadataLineageSource upsertAiNode(String tableName, Map<String, MetadataLineageSource> cache) {
        MetadataLineageSource node = cache.get(tableName);
        if (node == null) {
            node = sourceService.upsert(tableName, tableName, NODE_TYPE_TABLE, null, null, null, null, null, "AI 血缘识别自动注册");
            cache.put(tableName, node);
        }
        return node;
    }

    /** 查询指定节点关联的字段级血缘（钻取查询），并回填两端节点名称 */
    public List<MetadataColumnLineage> getColumnLineageBySource(Long sourceId) {
        List<MetadataColumnLineage> list = columnLineageMapper.selectList(
                new LambdaQueryWrapper<MetadataColumnLineage>()
                        .eq(MetadataColumnLineage::getSourceTableId, sourceId)
                        .or().eq(MetadataColumnLineage::getTargetTableId, sourceId)
                        .orderByAsc(MetadataColumnLineage::getId));
        fillTableNames(list);
        return list;
    }

    /**
     * 分页查询指定节点关联的字段级血缘（钻取查询），并回填两端节点名称
     * 供元数据管理"字段级血缘"Tab 分页加载，避免字段映射过多时一次性全量返回
     *
     * @param sourceId 血缘节点ID（lineage_source.id）
     * @param page     页码（从1开始，可空）
     * @param pageSize 每页条数（可空，默认10）
     * @return 分页字段级血缘（含两端节点名称回填）
     */
    public PageResult<MetadataColumnLineage> getColumnLineagePageBySource(Long sourceId, Integer page, Integer pageSize) {
        Page<MetadataColumnLineage> p = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<MetadataColumnLineage> wrapper = new LambdaQueryWrapper<MetadataColumnLineage>()
                .eq(MetadataColumnLineage::getSourceTableId, sourceId)
                .or().eq(MetadataColumnLineage::getTargetTableId, sourceId)
                .orderByAsc(MetadataColumnLineage::getId);
        Page<MetadataColumnLineage> result = columnLineageMapper.selectPage(p, wrapper);
        fillTableNames(result.getRecords());
        return PageResult.of(result);
    }

    /** 批量回填字段级血缘两端节点名称（sourceTableId/targetTableId → lineage_source.source_name） */
    private void fillTableNames(List<MetadataColumnLineage> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        for (MetadataColumnLineage cl : list) {
            if (cl.getSourceTableId() != null) ids.add(cl.getSourceTableId());
            if (cl.getTargetTableId() != null) ids.add(cl.getTargetTableId());
        }
        if (ids.isEmpty()) return;
        Map<Long, String> nameMap = new HashMap<>();
        for (MetadataLineageSource s : sourceService.listByIds(ids)) {
            nameMap.put(s.getId(), s.getSourceName() != null ? s.getSourceName() : s.getSourceCode());
        }
        for (MetadataColumnLineage cl : list) {
            cl.setSourceTableName(nameMap.get(cl.getSourceTableId()));
            cl.setTargetTableName(nameMap.get(cl.getTargetTableId()));
        }
    }

    /** 查询指定分层边下的字段级血缘 */
    public List<MetadataColumnLineage> getColumnLineageByHierarchy(Long hierarchyId) {
        return columnLineageMapper.selectList(
                new LambdaQueryWrapper<MetadataColumnLineage>()
                        .eq(MetadataColumnLineage::getHierarchyId, hierarchyId)
                        .orderByAsc(MetadataColumnLineage::getId));
    }

    /** 查询指定字段级血缘下的计算依赖 */
    public List<MetadataColumnCalcDependency> getCalcDependencyByColumnLineage(Long columnLineageId) {
        return calcDependencyMapper.selectList(
                new LambdaQueryWrapper<MetadataColumnCalcDependency>()
                        .eq(MetadataColumnCalcDependency::getTargetColumnLineageId, columnLineageId)
                        .orderByAsc(MetadataColumnCalcDependency::getCalcOrder));
    }

    /** 查询节点相关的分层边（作为上游或下游） */
    public List<MetadataLineageHierarchy> getHierarchyBySource(Long sourceId) {
        return hierarchyMapper.selectList(
                new LambdaQueryWrapper<MetadataLineageHierarchy>()
                        .eq(MetadataLineageHierarchy::getUpstreamSourceId, sourceId)
                        .or().eq(MetadataLineageHierarchy::getDownstreamSourceId, sourceId)
                        .orderByAsc(MetadataLineageHierarchy::getLayerLevel));
    }

    /** 查询报表解析记录 */
    public List<MetadataReportSqlParser> listReportParsers(String keyword, String status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<MetadataReportSqlParser> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(MetadataReportSqlParser::getReportName, keyword)
                    .or().like(MetadataReportSqlParser::getSqlContent, keyword));
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(MetadataReportSqlParser::getParseStatusId, status);
        }
        wrapper.orderByDesc(MetadataReportSqlParser::getCreatedAt);
        return reportParserMapper.selectList(wrapper);
    }

    /** 表级链路记录：source(来源表) → target(目标表或 REPORT)，depth=距消费端(报表)的依赖跳数（主表=1，逐级+1） */
    private record TableLink(String source, String target, String relationType, int layerLevel, int depth, String transformRule) {}

    /**
     * 构建表级血缘传导链路
     * 规则：主表(pr.tables[0]) → REPORT（应用层 DEPEND）；
     *       JOIN 引入的表 → 已纳入链路另一侧的表（物理层 REFERENCE，携带关联条件）；
     *       子查询占位别名穿透到内部主表（如 ia → DHC_BillConINV），内部 JOIN 递归接入。
     * 例：门诊工作量报表 ← PA_Adm ← {CT_Loc, PA_PatMas, CT_CareProv, DHC_BillConINV}；DHC_BillConINV ← DHC_INVPRT
     */
    private List<TableLink> buildTableLinks(LineageSQLParser.ParseResult pr) {
        List<TableLink> links = new ArrayList<>();
        if (pr == null || pr.tables.isEmpty()) return links;
        String mainTable = pr.tables.getFirst().name;
        Set<String> processed = new HashSet<>();
        // 各表距消费端(报表)的依赖跳数：主表=1，JOIN/子查询引入表逐级+1
        Map<String, Integer> depthMap = new HashMap<>();
        processed.add(mainTable.toLowerCase());
        depthMap.put(mainTable.toLowerCase(), 1);
        links.add(new TableLink(mainTable, "REPORT", "DEPEND", 1, 1, pr.reportSql));
        if (pr.joins != null) {
            for (LineageSQLParser.JoinRel join : pr.joins) {
                addJoinLink(pr, join, links, processed, depthMap);
            }
        }
        if (pr.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
                collectInnerJoinLinks(sub.inner, links, processed, depthMap);
            }
        }
        return links;
    }

    /** 处理一条 JOIN 的表级接入：新表指向已纳入链路的一侧 */
    private void addJoinLink(LineageSQLParser.ParseResult pr, LineageSQLParser.JoinRel join,
                             List<TableLink> links, Set<String> processed, Map<String, Integer> depthMap) {
        String from = resolveThroughSubQuery(resolveTableName(join.fromAlias, pr), pr);
        String to = resolveThroughSubQuery(resolveTableName(join.toAlias, pr), pr);
        if (from == null || to == null || from.equalsIgnoreCase(to)) return;
        String newTable, existTable;
        if (processed.contains(from.toLowerCase()) && !processed.contains(to.toLowerCase())) {
            existTable = from;
            newTable = to;
        } else if (processed.contains(to.toLowerCase()) && !processed.contains(from.toLowerCase())) {
            existTable = to;
            newTable = from;
        } else {
            return; // 两侧均已在链路或均未处理，跳过
        }
        processed.add(newTable.toLowerCase());
        // 依赖深度递增：新表距消费端跳数 = 已接入链路侧表 + 1（主表=1，如报表←事实表depth=1←字典表depth=2）
        int depth = depthMap.getOrDefault(existTable.toLowerCase(), 1) + 1;
        depthMap.put(newTable.toLowerCase(), depth);
        // 关联条件原文（真实表名.字段 = 真实表名.字段）
        String cond = resolveTableName(join.fromAlias, pr) + "." + join.fromColumn
                + " = " + resolveTableName(join.toAlias, pr) + "." + join.toColumn;
        links.add(new TableLink(newTable, existTable, "REFERENCE", 2, depth, cond));
    }

    /** 递归处理子查询内部 JOIN（子查询内部表已由外层占位别名穿透接入，深度随链路逐级递增） */
    private void collectInnerJoinLinks(LineageSQLParser.ParseResult inner,
                                       List<TableLink> links, Set<String> processed, Map<String, Integer> depthMap) {
        if (inner == null) return;
        if (inner.joins != null) {
            for (LineageSQLParser.JoinRel join : inner.joins) {
                addJoinLink(inner, join, links, processed, depthMap);
            }
        }
        if (inner.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : inner.subQueries) {
                collectInnerJoinLinks(sub.inner, links, processed, depthMap);
            }
        }
    }

    /** 子查询占位别名穿透为内部主表（如 ia → DHC_BillConINV）；非占位别名原样返回 */
    private String resolveThroughSubQuery(String aliasOrTable, LineageSQLParser.ParseResult pr) {
        if (aliasOrTable == null || pr.subQueries == null) return aliasOrTable;
        for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
            if (aliasOrTable.equalsIgnoreCase(sub.alias) && sub.inner != null && !sub.inner.tables.isEmpty()) {
                return sub.inner.tables.getFirst().name;
            }
        }
        return aliasOrTable;
    }

    /** 递归收集主查询与子查询内部全部表引用（子查询穿透落库依赖内部表节点） */
    private void collectAllTables(LineageSQLParser.ParseResult pr, List<LineageSQLParser.TableRef> out) {
        if (pr == null) return;
        if (pr.tables != null) out.addAll(pr.tables);
        if (pr.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
                collectAllTables(sub.inner, out);
            }
        }
    }

    /** 判断别名是否为任意层级子查询占位别名（支持嵌套子查询） */
    private boolean isSubQueryAlias(LineageSQLParser.ParseResult pr, String alias) {
        if (pr == null || alias == null) return false;
        if (pr.subQueryAliases.contains(alias)) return true;
        if (pr.subQueries != null) {
            for (LineageSQLParser.SubQueryRef sub : pr.subQueries) {
                if (isSubQueryAlias(sub.inner, alias)) return true;
            }
        }
        return false;
    }

    /** 通过子查询别名+输出列名定位子查询内部字段定义（穿透一层；先按输出别名，再回退来源字段名；支持嵌套） */
    private LineageSQLParser.ColumnRef findColumnInSubQuery(LineageSQLParser.ParseResult pr,
                                                            String subAlias, String columnName) {
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
            // 嵌套子查询：递归查找
            LineageSQLParser.ColumnRef found = findColumnInSubQuery(sub.inner, subAlias, columnName);
            if (found != null) return found;
        }
        return null;
    }

    /** 解析别名/表名为真实表名（子查询占位别名等无法解析时原样返回） */
    private String resolveTableName(String aliasOrTable, LineageSQLParser.ParseResult pr) {
        if (pr.tables != null) {
            for (LineageSQLParser.TableRef t : pr.tables) {
                if (aliasOrTable.equalsIgnoreCase(t.alias) || aliasOrTable.equalsIgnoreCase(t.name)) {
                    return t.name;
                }
            }
        }
        return aliasOrTable;
    }

    /** 构建字段血缘关联条件：内层 JOIN 溯源条件 + 外层子查询 JOIN 定位条件 */
    private String buildEffectiveJoinCondition(LineageSQLParser.ParseResult pr,
                                               LineageSQLParser.ColumnRef col,
                                               LineageSQLParser.ColumnRef resolved,
                                               boolean viaSubQuery) {
        List<String> conds = new ArrayList<>();
        if (StrUtil.isNotBlank(resolved.joinCondition)) {
            conds.add(resolved.joinCondition);
        }
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

    /** 获取首个分层边ID（当前报表解析场景：取该源表最近一条边） */
    private Long firstHierarchyId(LineageSQLParser.ParseResult pr,
                                  Map<String, MetadataLineageSource> tableNodeCache,
                                  MetadataLineageSource reportNode,
                                  MetadataLineageSource srcNode, String sql) {
        MetadataLineageHierarchy h = hierarchyMapper.selectOne(
                new LambdaQueryWrapper<MetadataLineageHierarchy>()
                        .eq(MetadataLineageHierarchy::getUpstreamSourceId, srcNode.getId())
                        .eq(MetadataLineageHierarchy::getDownstreamSourceId, reportNode.getId())
                        .last("LIMIT 1"));
        return h != null ? h.getId() : 0L;
    }

    /** 提取聚合函数（SUM→SUM） */
    private String extractAggregation(String function) {
        if (function == null) return null;
        String upper = function.toUpperCase();
        return switch (upper) {
            case "SUM", "AVG", "COUNT", "MAX", "MIN" -> upper;
            default -> null;
        };
    }

    /** 从表达式中提取主运算符 */
    private String extractOperator(String expression) {
        if (expression == null) return null;
        for (char c : expression.toCharArray()) {
            if ("+-*/%".indexOf(c) >= 0) return String.valueOf(c);
        }
        return null;
    }

    /** 构建血缘路径（表 → 报表） */
    private String buildLineagePath(String upstreamCode, String downstreamCode) {
        return upstreamCode + "→" + downstreamCode;
    }

    /** 序列化为 JSON（JSONB 列） */
    private String toJson(Object obj) {
        try {
            return obj == null ? null : new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("血缘解析结果 JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /** SHA-256 摘要 */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /** 补全审计字段 */
    private void fillAudit(Object entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity instanceof MetadataReportSqlParser p) {
            p.setCreatedAt(now); p.setUpdatedAt(now); p.setCreatedBy("system"); p.setUpdatedBy("system"); p.setStatus(1);
        } else if (entity instanceof MetadataLineageHierarchy h) {
            h.setCreatedAt(now); h.setUpdatedAt(now); h.setCreatedBy("system"); h.setUpdatedBy("system"); h.setStatus(1);
        } else if (entity instanceof MetadataColumnLineage c) {
            c.setCreatedAt(now); c.setUpdatedAt(now); c.setCreatedBy("system"); c.setUpdatedBy("system"); c.setStatus(1);
        } else if (entity instanceof MetadataColumnCalcDependency d) {
            d.setCreatedAt(now); d.setUpdatedAt(now); d.setCreatedBy("system"); d.setUpdatedBy("system"); d.setStatus(1);
        }
    }

    /** 补全宏观投影血缘审计字段 */
    private void fillLineageAudit(DamLineage lineage) {
        LocalDateTime now = LocalDateTime.now();
        lineage.setCreatedAt(now);
        lineage.setUpdatedAt(now);
        lineage.setCreatedBy("system");
        lineage.setUpdatedBy("system");
        lineage.setStatus(1);
    }
}
