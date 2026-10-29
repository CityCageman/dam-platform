package com.dam.lineage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.vo.PageResult;
import com.dam.lineage.entity.DamLineage;
import com.dam.lineage.entity.MetadataColumnLineage;
import com.dam.lineage.entity.MetadataLineageSource;
import com.dam.lineage.repository.DamLineageMapper;
import com.dam.lineage.repository.MetadataColumnLineageMapper;
import com.dam.lineage.repository.MetadataLineageSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据血缘服务
 * 适配新表 dam_metadata.metadata_lineage：
 *  - source_type/source_id/target_type/target_id 承载血缘节点（类型：TABLE/REPORT/VIEW/PROCEDURE/DICT 等）
 *  - transform_rule 承载转换规则 SQL
 *  - relation_type 承载关系类型（如 PARSER 解析所得）
 * 说明：血缘节点ID（source_id/target_id）为 metadata_lineage_source.id，与资产ID解耦；
 *      资产场景下沿用资产ID写入，两者均可通过本服务按 ID 查询。
 */
@Service
@RequiredArgsConstructor
public class LineageService {

    /** 血缘节点类型常量：表级血缘 */
    private static final String NODE_TYPE_TABLE = "TABLE";

    private final DamLineageMapper lineageMapper;
    private final LineageSQLParser sqlParser;
    /** 血缘节点注册表 Mapper（用于图谱节点名称回填） */
    private final MetadataLineageSourceMapper sourceMapper;
    /** 字段级血缘 Mapper（图谱字段级展开） */
    private final MetadataColumnLineageMapper columnLineageMapper;

    /**
     * 按节点ID查询血缘关系（作为源或目标节点，不限节点类型）
     *
     * @param nodeId 血缘节点ID（lineage_source.id）或资产ID
     * @return 血缘记录列表
     */
    public List<DamLineage> getLineageByAssetId(Long nodeId) {
        LambdaQueryWrapper<DamLineage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DamLineage::getSourceId, nodeId)
                .or()
                .eq(DamLineage::getTargetId, nodeId);
        List<DamLineage> list = lineageMapper.selectList(wrapper);
        // 批量回填血缘节点名称（source_node_name/target_node_name，便于前端展示）
        fillNodeNames(list);
        // 填充前端兼容字段（sourceAssetId/targetAssetId/transformSql/lineageType）
        for (DamLineage l : list) {
            fillLineageCompat(l);
        }
        return list;
    }

    /**
     * 按节点ID分页查询血缘关系（作为源或目标节点，不限节点类型）
     * 供元数据管理"数据血缘与依赖"Tab 分页加载，避免大表血缘过多时一次性全量返回
     *
     * @param nodeId   血缘节点ID（lineage_source.id）或资产ID
     * @param page     页码（从1开始，可空）
     * @param pageSize 每页条数（可空，默认10）
     * @return 分页血缘记录（含节点名称回填与前端兼容字段）
     */
    public PageResult<DamLineage> getLineagePageByNodeId(Long nodeId, Integer page, Integer pageSize) {
        Page<DamLineage> p = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<DamLineage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DamLineage::getSourceId, nodeId)
                .or()
                .eq(DamLineage::getTargetId, nodeId)
                .orderByDesc(DamLineage::getId);
        Page<DamLineage> result = lineageMapper.selectPage(p, wrapper);
        // 批量回填血缘节点名称（source_node_name/target_node_name，便于前端展示）
        fillNodeNames(result.getRecords());
        // 填充前端兼容字段（sourceAssetId/targetAssetId/transformSql/lineageType）
        for (DamLineage l : result.getRecords()) {
            fillLineageCompat(l);
        }
        return PageResult.of(result);
    }

    /** 批量回填血缘边两端节点的名称 */
    private void fillNodeNames(List<DamLineage> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        for (DamLineage l : list) {
            ids.add(l.getSourceId());
            ids.add(l.getTargetId());
        }
        Map<Long, String> nameMap = loadNodeNames(ids);
        for (DamLineage l : list) {
            l.setSourceNodeName(nameMap.get(l.getSourceId()));
            l.setTargetNodeName(nameMap.get(l.getTargetId()));
        }
    }

    /**
     * 填充血缘前端兼容字段（sourceAssetId/targetAssetId/transformSql/lineageType/jobName）
     *
     * @param l 血缘实体（兼容字段不持久化）
     */
    private void fillLineageCompat(DamLineage l) {
        if (l == null) return;
        if (l.getSourceAssetId() == null && NODE_TYPE_TABLE.equals(l.getSourceType())) {
            l.setSourceAssetId(l.getSourceId());
        }
        if (l.getTargetAssetId() == null && NODE_TYPE_TABLE.equals(l.getTargetType())) {
            l.setTargetAssetId(l.getTargetId());
        }
        if (l.getTransformSql() == null) l.setTransformSql(l.getTransformRule());
        if (l.getLineageType() == null) l.setLineageType(l.getRelationType());
        if (l.getJobName() == null) l.setJobName(l.getDiscoveredBy());
    }

    /**
     * 构建血缘关系图（供前端 d3 渲染）
     * 以指定节点为中心，按依赖深度收集上下游血缘边，组装 nodes/edges。
     * <ul>
     *   <li>depth=1：仅直接上下游（默认），双击节点前端下钻时以新中心重查</li>
     *   <li>平行边聚合：同一 source→target 的 N 条字段引用边合并为 1 条（count=N，labels 携带各关联条件）</li>
     *   <li>includeFields=true：附带中心节点相关的字段级节点/边（F:表ID:字段名，供字段级开关展开）</li>
     * </ul>
     *
     * @param nodeId        中心节点ID（lineage_source.id）或资产ID
     * @param depth         依赖深度（>=1，默认1）
     * @param includeFields 是否附带字段级节点/边
     * @return 图数据 { nodes: [{id,label,type,isCenter}], edges: [{source,target,relationType,dependencyLevel,count,labels}] }
     */
    public Map<String, Object> getLineageGraph(Long nodeId, Integer depth, Boolean includeFields) {
        int maxDepth = (depth == null || depth < 1) ? 1 : depth;
        List<DamLineage> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();
        // 上游（数据来源）：target=nodeId 的边，递归 maxDepth 跳
        collectUpstream(nodeId, new HashSet<>(), edges, edgeKeys, 0, maxDepth);
        // 下游（数据去向）：source=nodeId 的边，递归 maxDepth 跳
        collectDownstream(nodeId, new HashSet<>(), edges, edgeKeys, 0, maxDepth);

        // 收集全部节点ID并回填节点名称
        Set<Long> nodeIds = new HashSet<>();
        for (DamLineage edge : edges) {
            nodeIds.add(edge.getSourceId());
            nodeIds.add(edge.getTargetId());
        }
        nodeIds.add(nodeId);
        Map<Long, String> nameMap = loadNodeNames(nodeIds);

        // 节点集合（去重）与边集合（平行边聚合）
        Set<Object> added = new HashSet<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edgeList = new ArrayList<>();

        // 中心节点始终在图中（突出展示）
        Map<String, Object> centerNode = new HashMap<>();
        centerNode.put("id", nodeId);
        centerNode.put("label", nameMap.getOrDefault(nodeId, "节点:" + nodeId));
        centerNode.put("type", resolveNodeType(nodeId));
        centerNode.put("isCenter", true);
        nodes.add(centerNode);
        added.add(nodeId);

        for (DamLineage edge : edges) {
            if (!added.contains(edge.getSourceId())) {
                Map<String, Object> srcNode = new HashMap<>();
                srcNode.put("id", edge.getSourceId());
                srcNode.put("label", nameMap.getOrDefault(edge.getSourceId(), "源:" + edge.getSourceId()));
                srcNode.put("type", edge.getSourceType() != null ? edge.getSourceType() : "source");
                srcNode.put("isCenter", false);
                nodes.add(srcNode);
                added.add(edge.getSourceId());
            }
            if (!added.contains(edge.getTargetId())) {
                Map<String, Object> tgtNode = new HashMap<>();
                tgtNode.put("id", edge.getTargetId());
                tgtNode.put("label", nameMap.getOrDefault(edge.getTargetId(), "目标:" + edge.getTargetId()));
                tgtNode.put("type", edge.getTargetType() != null ? edge.getTargetType() : "target");
                tgtNode.put("isCenter", false);
                nodes.add(tgtNode);
                added.add(edge.getTargetId());
            }
        }

        // 平行边聚合：同一 source→target 合并为 1 条，count=N，labels 汇总各关联条件
        Map<String, List<DamLineage>> grouped = new LinkedHashMap<>();
        for (DamLineage edge : edges) {
            String key = edge.getSourceId() + "->" + edge.getTargetId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
        }
        for (Map.Entry<String, List<DamLineage>> en : grouped.entrySet()) {
            List<DamLineage> list = en.getValue();
            DamLineage first = list.getFirst();
            Map<String, Object> edgeMap = new HashMap<>();
            edgeMap.put("source", first.getSourceId());
            edgeMap.put("target", first.getTargetId());
            edgeMap.put("relationType", first.getRelationType());
            edgeMap.put("dependencyLevel", first.getDependencyLevel() != null ? first.getDependencyLevel() : 1);
            edgeMap.put("count", list.size());
            edgeMap.put("labels", list.stream()
                    .map(DamLineage::getTransformRule)
                    .filter(s -> s != null && !s.isEmpty())
                    .distinct()
                    .toList());
            edgeList.add(edgeMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edgeList);
        result.put("centerNodeId", nodeId);
        // 字段级展开：仅当开启时，附带中心节点相关的字段级节点/边
        if (Boolean.TRUE.equals(includeFields)) {
            appendFieldEdges(nodeId, nameMap, nodes, edgeList, added);
        }
        return result;
    }

    /** 沿来源方向递归收集上游链路（metadata_lineage 中 target=nodeId 的边为直接上游），最大 maxDepth 跳 */
    private void collectUpstream(Long nodeId, Set<Long> visited, List<DamLineage> out,
                                 Set<String> edgeKeys, int depth, int maxDepth) {
        if (nodeId == null || depth >= maxDepth || !visited.add(nodeId)) return;
        List<DamLineage> edges = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getTargetId, nodeId));
        for (DamLineage e : edges) {
            addEdgeOnce(out, edgeKeys, e);
            collectUpstream(e.getSourceId(), visited, out, edgeKeys, depth + 1, maxDepth);
        }
    }

    /** 沿去向方向递归收集下游链路（metadata_lineage 中 source=nodeId 的边为直接下游），最大 maxDepth 跳 */
    private void collectDownstream(Long nodeId, Set<Long> visited, List<DamLineage> out,
                                   Set<String> edgeKeys, int depth, int maxDepth) {
        if (nodeId == null || depth >= maxDepth || !visited.add(nodeId)) return;
        List<DamLineage> edges = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>().eq(DamLineage::getSourceId, nodeId));
        for (DamLineage e : edges) {
            addEdgeOnce(out, edgeKeys, e);
            collectDownstream(e.getTargetId(), visited, out, edgeKeys, depth + 1, maxDepth);
        }
    }

    /** 附带中心节点相关的字段级血缘节点/边（字段节点 id=F:表ID:字段名，type=FIELD） */
    private void appendFieldEdges(Long centerId, Map<Long, String> nameMap,
                                  List<Map<String, Object>> nodes, List<Map<String, Object>> edgeList,
                                  Set<Object> added) {
        List<MetadataColumnLineage> cls = columnLineageMapper.selectList(
                new LambdaQueryWrapper<MetadataColumnLineage>()
                        .eq(MetadataColumnLineage::getSourceTableId, centerId)
                        .or().eq(MetadataColumnLineage::getTargetTableId, centerId));
        for (MetadataColumnLineage cl : cls) {
            if (cl.getSourceTableId() == null || cl.getTargetTableId() == null) continue;
            // 仅对图谱中已出现的表节点展开字段级（避免字段节点泛滥）
            String srcId = "F:" + cl.getSourceTableId() + ":" + cl.getSourceColumnName();
            String tgtId = "F:" + cl.getTargetTableId() + ":" + cl.getTargetColumnName();
            addFieldNode(nodes, added, srcId, nameMap.get(cl.getSourceTableId()), cl.getSourceColumnName());
            addFieldNode(nodes, added, tgtId, nameMap.get(cl.getTargetTableId()), cl.getTargetColumnName());
            Map<String, Object> f = new HashMap<>();
            f.put("source", srcId);
            f.put("target", tgtId);
            f.put("relationType", cl.getMappingTypeId());
            f.put("dependencyLevel", 1);
            f.put("count", 1);
            f.put("labels", List.of(cl.getTransformExpression() != null ? cl.getTransformExpression() : cl.getJoinCondition()));
            edgeList.add(f);
        }
    }

    /** 添加字段级节点（去重；label=字段名，name=表.字段 供 tooltip） */
    private void addFieldNode(List<Map<String, Object>> nodes, Set<Object> added,
                              String id, String tableName, String columnName) {
        if (columnName == null || added.contains(id)) return;
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("label", columnName);
        node.put("name", (tableName != null ? tableName + "." : "") + columnName);
        node.put("type", "FIELD");
        node.put("isCenter", false);
        nodes.add(node);
        added.add(id);
    }

    /** 查询节点类型（lineage_source.source_type_id，未找到默认 TABLE） */
    private String resolveNodeType(Long nodeId) {
        MetadataLineageSource s = sourceMapper.selectById(nodeId);
        return s != null && s.getSourceTypeId() != null ? s.getSourceTypeId() : "TABLE";
    }

    /** 边去重追加（sourceId->targetId 唯一，历史多次解析产生的重复边只保留一条） */
    private void addEdgeOnce(List<DamLineage> out, Set<String> edgeKeys, DamLineage e) {
        if (e == null) return;
        String key = e.getSourceId() + "->" + e.getTargetId();
        if (edgeKeys.add(key)) out.add(e);
    }

    /** 批量回填节点名称（lineage_source.source_name/source_code） */
    private Map<Long, String> loadNodeNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        List<MetadataLineageSource> list = sourceMapper.selectList(
                new LambdaQueryWrapper<MetadataLineageSource>().in(MetadataLineageSource::getId, ids));
        for (MetadataLineageSource s : list) {
            map.put(s.getId(), s.getSourceName() != null ? s.getSourceName() : s.getSourceCode());
        }
        return map;
    }

    /**
     * 手动创建血缘记录
     * 自动补全审计字段（created_at/updated_at/created_by/updated_by）
     *
     * @param lineage 血缘实体
     * @return 创建后的血缘记录（含自增ID）
     */
    @Transactional
    public DamLineage createLineage(DamLineage lineage) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        lineage.setCreatedAt(now);
        lineage.setUpdatedAt(now);
        if (lineage.getCreatedBy() == null) lineage.setCreatedBy("system");
        if (lineage.getUpdatedBy() == null) lineage.setUpdatedBy("system");
        lineageMapper.insert(lineage);
        return lineage;
    }

    /**
     * 删除血缘记录（@TableLogic 逻辑删除，自动置 deleted_at）
     *
     * @param id 血缘记录ID
     */
    @Transactional
    public void deleteLineage(Long id) {
        lineageMapper.deleteById(id);
    }

    // ==================== SQL解析构建血缘 ====================

    /**
     * 解析SQL并自动构建表级血缘
     * 将解析器产出的 LineageRelation 映射为 metadata_lineage 记录：
     *  - 表名哈希 → source_id/target_id（节点类型固定 TABLE）
     *  - SQL → transform_rule
     *  - 关系类型 → relation_type
     *  - 来源系统 → source_system
     *
     * @param sql          待解析的SQL语句
     * @param jobName      采集作业名（写入 discovered_by）
     * @param sourceSystem 来源系统标识
     * @return 创建的血缘记录列表
     */
    @Transactional
    public List<DamLineage> parseAndCreate(String sql, String jobName, String sourceSystem) {
        // 调用 SQL 解析器提取血缘关系
        List<LineageSQLParser.LineageRelation> relations = sqlParser.parseSql(sql, jobName, sourceSystem);
        List<DamLineage> created = new ArrayList<>();
        for (LineageSQLParser.LineageRelation rel : relations) {
            DamLineage lineage = new DamLineage();
            // 源节点：类型固定 TABLE，ID 由表名哈希得到
            lineage.setSourceType(NODE_TYPE_TABLE);
            lineage.setSourceId(hashTableName(rel.sourceTable()));
            // 目标节点：类型固定 TABLE，ID 由表名哈希得到
            lineage.setTargetType(NODE_TYPE_TABLE);
            lineage.setTargetId(hashTableName(rel.targetTable()));
            // 关系类型：数据流向（上游→下游）
            lineage.setRelationType(rel.lineageType() != null ? rel.lineageType() : "FLOW");
            // 转换规则：承载原始SQL片段
            lineage.setTransformRule(rel.sql());
            // 依赖层级：默认1级
            lineage.setDependencyLevel(1);
            // 来源系统：取自解析器或入参
            lineage.setSourceSystem(rel.sourceSystem() != null ? rel.sourceSystem() : sourceSystem);
            // 发现方式：自动解析
            lineage.setDiscoveredBy(jobName);
            // 补全审计字段
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            lineage.setCreatedAt(now);
            lineage.setUpdatedAt(now);
            lineage.setCreatedBy("system");
            lineage.setUpdatedBy("system");
            lineageMapper.insert(lineage);
            created.add(lineage);
        }
        return created;
    }

    // ==================== 影响分析 ====================

    /**
     * 影响分析：递归计算资产的全部下游影响与上游溯源
     *
     * @param nodeId 中心节点ID（lineage_source.id）或资产ID
     * @return 影响分析结果 { directDownstream, totalDownstream, directUpstream, totalUpstream, ... }
     */
    public Map<String, Object> impactAnalysis(Long nodeId) {
        // 直接下游：以该节点为源节点的血缘（目标节点即下游）
        List<DamLineage> downstream = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>()
                        .eq(DamLineage::getSourceId, nodeId));
        // 直接上游：以该节点为目标节点的血缘（源节点即上游）
        List<DamLineage> upstream = lineageMapper.selectList(
                new LambdaQueryWrapper<DamLineage>()
                        .eq(DamLineage::getTargetId, nodeId));

        // 直接影响的节点ID集合
        Set<Long> downstreamIds = downstream.stream().map(DamLineage::getTargetId).collect(Collectors.toSet());
        Set<Long> upstreamIds = upstream.stream().map(DamLineage::getSourceId).collect(Collectors.toSet());

        // 递归获取多层影响（沿血缘边逐级扩散）
        Set<Long> allDownstream = new HashSet<>(downstreamIds);
        Set<Long> allUpstream = new HashSet<>(upstreamIds);
        expandDownstream(downstreamIds, allDownstream);
        expandUpstream(upstreamIds, allUpstream);

        Map<String, Object> result = new HashMap<>();
        result.put("assetId", nodeId);
        result.put("directDownstream", downstreamIds.size());
        result.put("totalDownstream", allDownstream.size());
        result.put("directUpstream", upstreamIds.size());
        result.put("totalUpstream", allUpstream.size());
        result.put("downstreamIds", allDownstream);
        result.put("upstreamIds", allUpstream);
        return result;
    }

    /**
     * 递归向下扩展：找出所有下游影响的节点
     *
     * @param current 当前层待扩展的源节点ID集合
     * @param all     累积的全部下游节点ID集合（去重）
     */
    private void expandDownstream(Set<Long> current, Set<Long> all) {
        for (Long id : current) {
            // 查询以 id 为源节点的下一层血缘
            List<DamLineage> next = lineageMapper.selectList(
                    new LambdaQueryWrapper<DamLineage>()
                            .eq(DamLineage::getSourceId, id));
            for (DamLineage l : next) {
                if (all.add(l.getTargetId())) {
                    // 发现新的下游节点，继续递归
                    Set<Long> nextSet = new HashSet<>();
                    nextSet.add(l.getTargetId());
                    expandDownstream(nextSet, all);
                }
            }
        }
    }

    /**
     * 递归向上扩展：找出所有上游溯源节点
     *
     * @param current 当前层待扩展的目标节点ID集合
     * @param all     累积的全部上游节点ID集合（去重）
     */
    private void expandUpstream(Set<Long> current, Set<Long> all) {
        for (Long id : current) {
            // 查询以 id 为目标节点的上一层血缘
            List<DamLineage> prev = lineageMapper.selectList(
                    new LambdaQueryWrapper<DamLineage>()
                            .eq(DamLineage::getTargetId, id));
            for (DamLineage l : prev) {
                if (all.add(l.getSourceId())) {
                    // 发现新的上游节点，继续递归
                    Set<Long> prevSet = new HashSet<>();
                    prevSet.add(l.getSourceId());
                    expandUpstream(prevSet, all);
                }
            }
        }
    }

    /**
     * 表名哈希为节点ID（避免依赖资产ID，兼容未注册资产）
     *
     * @param tableName 表名
     * @return 非负哈希值
     */
    private Long hashTableName(String tableName) {
        return (long) Math.abs(tableName.hashCode());
    }
}
