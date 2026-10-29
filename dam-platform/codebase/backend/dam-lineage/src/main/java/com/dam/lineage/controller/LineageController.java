package com.dam.lineage.controller;

import cn.hutool.core.util.StrUtil;
import com.dam.lineage.entity.*;
import com.dam.lineage.service.LineagePersistService;
import com.dam.lineage.service.LineageService;
import com.dam.lineage.service.LineageSourceService;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据血缘接口
 * <p>
 * 提供：
 * <ul>
 *   <li>按资产查询血缘关系（源/目标节点）</li>
 *   <li>血缘关系图构建（供前端 ECharts 渲染）</li>
 *   <li>SQL 解析自动构建血缘</li>
 *   <li>影响分析（上游溯源/下游影响递归）</li>
 *   <li>血缘节点注册（报表/视图/存储过程/表/字典）</li>
 *   <li>报表 SQL 入库解析（宏观投影 + 字段级血缘 + 计算依赖）</li>
 *   <li>精细血缘钻取（分层边 → 字段级 → 计算依赖）</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/lineage")
@RequiredArgsConstructor
public class LineageController {

    /** 数据血缘服务 */
    private final LineageService lineageService;
    /** 血缘节点注册服务 */
    private final LineageSourceService sourceService;
    /** 血缘精细落库服务 */
    private final LineagePersistService persistService;

    /**
     * 查询指定资产的血缘关系列表（作为源或目标节点）
     * 注意：路径限定为数字，避免与 /column-lineage、/hierarchy 等静态路径歧义
     *
     * @param assetId 资产ID
     * @return 血缘记录列表
     */
    @GetMapping("/{assetId:\\d+}")
    public Result<List<DamLineage>> getLineage(@PathVariable Long assetId) {
        return Result.success(lineageService.getLineageByAssetId(assetId));
    }

    /**
     * 分页查询指定节点的血缘关系列表（作为源或目标节点）
     * 供元数据管理"数据血缘与依赖"Tab 分页加载，避免大表血缘过多时一次性全量返回
     *
     * @param nodeId   血缘节点ID（lineage_source.id）或资产ID
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @return 分页血缘记录
     */
    @GetMapping("/page/{nodeId:\\d+}")
    public Result<PageResult<DamLineage>> getLineagePage(@PathVariable Long nodeId,
                                                         @RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(lineageService.getLineagePageByNodeId(nodeId, page, pageSize));
    }

    /**
     * 构建血缘关系图数据（nodes/edges，供前端 d3 渲染）
     * <ul>
     *   <li>depth：依赖深度，默认 1（仅直接上下游），下钻时前端传新中心节点ID重新请求</li>
     *   <li>includeFields：是否附带字段级节点/边（字段级开关，默认 false）</li>
     * </ul>
     *
     * @param assetId       中心节点ID/资产ID
     * @param depth         依赖深度（>=1）
     * @param includeFields 是否附带字段级
     * @return 图数据 { nodes, edges, centerNodeId }
     */
    @GetMapping("/graph/{assetId:\\d+}")
    public Result<Map<String, Object>> getLineageGraph(@PathVariable Long assetId,
                                                       @RequestParam(defaultValue = "1") Integer depth,
                                                       @RequestParam(defaultValue = "false") Boolean includeFields) {
        return Result.success(lineageService.getLineageGraph(assetId, depth, includeFields));
    }

    /**
     * 手动创建血缘记录（自动补全审计字段）
     */
    @PostMapping
    public Result<DamLineage> createLineage(@RequestBody DamLineage lineage) {
        return Result.success(lineageService.createLineage(lineage));
    }

    /**
     * 删除血缘记录（软删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteLineage(@PathVariable Long id) {
        lineageService.deleteLineage(id);
        return Result.success(null);
    }

    // ==================== SQL解析构建血缘 ====================

    /**
     * 解析 SQL 并自动构建表级血缘记录
     *
     * @param params 请求体：sql-待解析SQL，jobName-采集作业名，sourceSystem-来源系统
     * @return 创建的血缘记录列表
     */
    @PostMapping("/parse-sql")
    public Result<List<DamLineage>> parseSql(@RequestBody Map<String, String> params) {
        return Result.success(lineageService.parseAndCreate(
                params.get("sql"),
                params.get("jobName"),
                params.get("sourceSystem")));
    }

    // ==================== 影响分析 ====================

    /**
     * 影响分析：递归计算资产的直接/全部上下游影响
     *
     * @param assetId 中心资产ID
     * @return 影响分析结果（directDownstream/totalDownstream 等）
     */
    @GetMapping("/impact/{assetId:\\d+}")
    public Result<Map<String, Object>> impactAnalysis(@PathVariable Long assetId) {
        return Result.success(lineageService.impactAnalysis(assetId));
    }

    // ==================== 血缘节点管理 ====================

    /**
     * 血缘节点分页列表
     *
     * @param typeId      节点类型（可空，lineage_node_type 字典值）
     * @param keyword     关键字（编码/名称模糊，可空）
     * @param categoryIds 资产分类ID集合（可空，逗号分隔，如 1,2,3；含子分类时前端展开传入）
     * @param page        页码
     * @param pageSize    每页大小
     * @return 分页节点列表
     */
    @GetMapping("/sources")
    public Result<PageResult<MetadataLineageSource>> listSources(
            @RequestParam(required = false) String typeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        List<Long> ids = null;
        if (StrUtil.isNotBlank(categoryIds)) {
            ids = Arrays.stream(categoryIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .toList();
        }
        return Result.success(sourceService.list(typeId, keyword, ids, page, pageSize));
    }

    /**
     * 手动注册血缘节点
     */
    @PostMapping("/sources")
    public Result<MetadataLineageSource> createSource(@RequestBody MetadataLineageSource source) {
        return Result.success(sourceService.create(source));
    }

    /**
     * 更新血缘节点
     */
    @PutMapping("/sources")
    public Result<MetadataLineageSource> updateSource(@RequestBody MetadataLineageSource source) {
        return Result.success(sourceService.update(source));
    }

    /**
     * 删除血缘节点（软删除）
     */
    @DeleteMapping("/sources/{id}")
    public Result<Void> deleteSource(@PathVariable Long id) {
        sourceService.delete(id);
        return Result.success(null);
    }

    // ==================== 报表SQL入库解析（微观精细血缘） ====================

    /**
     * 保存报表/视图 SQL 并完成全链路血缘落库
     * <p>
     * 链路：报表SQL → report_sql_parser（原文+解析结果）→ lineage_source（节点自动注册）
     * → lineage_hierarchy（分层精细边）+ metadata_lineage（宏观投影）
     * → column_lineage（字段级血缘）→ column_calc_dependency（计算依赖）
     *
     * @param params 请求体：reportCode-节点编码、reportName-报表名、reportType-类型、
     *               sql-SQL内容、categoryId-资产分类ID、sourceSystem-来源系统、
     *               replace-是否全量替换（true 清旧建新）、collectionId-采集记录ID
     * @return 落库摘要
     */
    @PostMapping("/report-sql")
    public Result<Map<String, Object>> parseReportSql(@RequestBody Map<String, Object> params) {
        String reportCode = (String) params.get("reportCode");
        String reportName = (String) params.get("reportName");
        String reportType = (String) params.get("reportType");
        String sql = (String) params.get("sql");
        String sourceSystem = (String) params.get("sourceSystem");
        boolean replace = Boolean.TRUE.equals(params.get("replace"));
        Long categoryId = params.get("categoryId") != null
                ? ((Number) params.get("categoryId")).longValue() : null;
        Long collectionId = params.get("collectionId") != null
                ? ((Number) params.get("collectionId")).longValue() : null;
        return Result.success(persistService.saveAndParseReport(
                reportCode, reportName, reportType, sql, sourceSystem, categoryId, collectionId, replace));
    }

    /**
     * 报表 SQL 解析记录列表
     */
    @GetMapping("/report-parsers")
    public Result<List<MetadataReportSqlParser>> listReportParsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(persistService.listReportParsers(keyword, status, 1, 100));
    }

    /**
     * 手动创建字段级血缘（业务表外键引用主数据表）
     * 自动生成分层边 + 字段级血缘 + 宏观投影
     */
    @PostMapping("/column-lineage")
    public Result<MetadataColumnLineage> createColumnLineage(@RequestBody Map<String, Object> params) {
        Long sourceId = params.get("sourceId") != null ? ((Number) params.get("sourceId")).longValue() : null;
        Long targetId = params.get("targetId") != null ? ((Number) params.get("targetId")).longValue() : null;
        return Result.success(persistService.createManualColumnLineage(
                sourceId,
                (String) params.get("sourceColumn"),
                targetId,
                (String) params.get("targetColumn"),
                (String) params.get("mappingType"),
                (String) params.get("joinCondition"),
                (String) params.get("transformExpression"),
                (String) params.get("relationType")));
    }

    /**
     * 批量落库外键引用血缘（扫描自动检测结果）
     *
     * @param params 请求体：foreignKeys-外键列表[{fromTable,fromColumn,toTable,toColumn,fkName}]、
     *               sourceSystem-来源系统、collectionId-采集记录ID
     * @return 落库摘要 {created, skipped}
     */
    @PostMapping("/fk-lineage")
    public Result<Map<String, Object>> saveFkLineage(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) params.get("foreignKeys");
        String sourceSystem = (String) params.get("sourceSystem");
        Long collectionId = params.get("collectionId") != null
                ? ((Number) params.get("collectionId")).longValue() : null;
        return Result.success(persistService.saveForeignKeyLineage(foreignKeys, sourceSystem, collectionId));
    }

    /**
     * 批量落库 AI 识别的数据血缘（表级 + 字段级分开）
     *
     * @param params 请求体：tableLineages-表级血缘[{sourceTable,targetTable,relationType,transformRule}]、
     *               columnLineages-字段级血缘[{sourceTable,sourceColumn,targetTable,targetColumn,mappingType,...}]、
     *               sourceSystem-来源系统、collectionId-采集记录ID
     * @return 落库摘要 {tableCreated,columnCreated,skipped}
     */
    @PostMapping("/ai-lineage")
    public Result<Map<String, Object>> saveAiLineage(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tableLineages = (List<Map<String, Object>>) params.get("tableLineages");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columnLineages = (List<Map<String, Object>>) params.get("columnLineages");
        String sourceSystem = (String) params.get("sourceSystem");
        Long collectionId = params.get("collectionId") != null
                ? ((Number) params.get("collectionId")).longValue() : null;
        return Result.success(persistService.saveAiLineage(tableLineages, columnLineages, sourceSystem, collectionId));
    }

    /**
     * 数据中台血缘上报（ingest）：按 taskId 覆盖式落库。
     *
     * @param params 请求体：taskId-中台任务标识（用于覆盖式更新）、sourceSystem-来源系统、
     *               tableLineages-表级血缘、columnLineages-字段级血缘
     * @return 落库摘要 {tableCreated,columnCreated,skipped}
     */
    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingestLineage(@RequestBody Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        String sourceSystem = (String) params.get("sourceSystem");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tableLineages = (List<Map<String, Object>>) params.get("tableLineages");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columnLineages = (List<Map<String, Object>>) params.get("columnLineages");
        return Result.success(persistService.ingestLineage(taskId, sourceSystem, tableLineages, columnLineages));
    }

    // ==================== 精细血缘钻取 ====================

    /**
     * 查询节点的分层血缘边（作为上游或下游）
     *
     * @param sourceId 血缘节点ID（metadata_lineage_source.id）
     * @return 分层血缘边列表
     */
    @GetMapping("/hierarchy")
    public Result<List<MetadataLineageHierarchy>> getHierarchy(@RequestParam Long sourceId) {
        return Result.success(persistService.getHierarchyBySource(sourceId));
    }

    /**
     * 查询分层血缘边下的字段级血缘
     *
     * @param hierarchyId 分层血缘边ID
     * @return 字段级血缘列表
     */
    @GetMapping("/column-lineage")
    public Result<List<MetadataColumnLineage>> getColumnLineage(@RequestParam Long hierarchyId) {
        return Result.success(persistService.getColumnLineageByHierarchy(hierarchyId));
    }

    /**
     * 查询血缘节点的字段列表（节点作为源或目标的全部字段级血缘，供抽屉"字段列表"展示）
     *
     * @param sourceId 血缘节点ID（metadata_lineage_source.id）
     * @return 字段级血缘列表
     */
    @GetMapping("/fields")
    public Result<List<MetadataColumnLineage>> getNodeFields(@RequestParam Long sourceId) {
        return Result.success(persistService.getColumnLineageBySource(sourceId));
    }

    /**
     * 分页查询血缘节点的字段级血缘列表
     * 供元数据管理"字段级血缘"Tab 分页加载，避免字段映射过多时一次性全量返回
     *
     * @param sourceId 血缘节点ID（lineage_source.id）
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页条数（默认10）
     * @return 分页字段级血缘
     */
    @GetMapping("/fields/page")
    public Result<PageResult<MetadataColumnLineage>> getNodeFieldsPage(@RequestParam Long sourceId,
                                                                       @RequestParam(defaultValue = "1") Integer page,
                                                                       @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(persistService.getColumnLineagePageBySource(sourceId, page, pageSize));
    }

    /**
     * 查询字段级血缘下的计算依赖
     *
     * @param columnLineageId 字段级血缘ID
     * @return 计算依赖列表
     */
    @GetMapping("/calc-dependency")
    public Result<List<MetadataColumnCalcDependency>> getCalcDependency(@RequestParam Long columnLineageId) {
        return Result.success(persistService.getCalcDependencyByColumnLineage(columnLineageId));
    }
}
