package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.*;
import com.dam.catalog.repository.*;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 技术元数据服务（对应文档 dam_metadata 模块职责）
 * <p>
 * 职责分离：本服务只负责技术元数据的采集落库（metadata_schema/metadata_table/metadata_column/metadata_management）
 * 与扫描作业管理（dam_scan_job），不直接操作资产表 dam_asset/dam_field。
 * 资产注册须通过 CatalogService 的注册入口完成。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalMetadataService {

    private final MetadataSchemaMapper schemaMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataManagementMapper managementMapper;
    private final DamScanJobMapper scanJobMapper;
    private final DamDataSourceMapper dataSourceMapper;

    // ============================================================
    //  扫描作业管理
    // ============================================================

    /**
     * 创建扫描作业并返回作业ID
     */
    @Transactional
    public Long createScanJob(Long dataSourceId, String databaseName, String host, Integer port,
                              String sourceType, Long operatorId) {
        DamScanJob job = new DamScanJob();
        job.setDataSourceId(dataSourceId);
        // metadata_collection.schema_id 为 NOT NULL，无归属时用 0 兑底
        job.setSchemaId(0L);
        job.setDatabaseName(databaseName);
        // metadata_collection.job_type 为 NOT NULL，sourceType 为空时兑底
        job.setSourceType(StrUtil.isNotBlank(sourceType) ? sourceType : "SCAN");
        job.setStatus(0);
        job.setStartTime(LocalDateTime.now());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setCreatedBy("system");
        job.setUpdatedBy("system");
        scanJobMapper.insert(job);
        return job.getId();
    }

    /**
     * 更新扫描作业状态与统计
     */
    @Transactional
    public void finishScanJob(Long jobId, String status, Integer tableCount, Integer fieldCount,
                              Integer schemaCount, String snapshotJson, String changesJson, String errorMsg) {
        DamScanJob job = scanJobMapper.selectById(jobId);
        if (job == null) return;
        job.setStatus("success".equalsIgnoreCase(status) ? 1 : ("failed".equalsIgnoreCase(status) ? 2 : 0));
        job.setEndTime(LocalDateTime.now());
        if (tableCount != null) job.setTableCount(tableCount);
        if (fieldCount != null) job.setFieldCount(fieldCount);
        if (schemaCount != null) job.setSchemaCount(schemaCount);
        if (snapshotJson != null) job.setSnapshotJson(snapshotJson);
        if (errorMsg != null) job.setErrorMsg(errorMsg);
        scanJobMapper.updateById(job);
    }

    /**
     * 查询扫描作业列表
     */
    public List<DamScanJob> getScanJobs(Long dataSourceId, String status) {
        LambdaQueryWrapper<DamScanJob> wrapper = new LambdaQueryWrapper<>();
        if (dataSourceId != null) {
            wrapper.eq(DamScanJob::getDataSourceId, dataSourceId);
        }
        if (StrUtil.isNotBlank(status)) {
            Integer code = "success".equalsIgnoreCase(status) ? 1 : ("failed".equalsIgnoreCase(status) ? 2 : 0);
            wrapper.eq(DamScanJob::getStatus, code);
        }
        wrapper.orderByDesc(DamScanJob::getCreatedAt);
        return scanJobMapper.selectList(wrapper);
    }

    /**
     * 查询扫描作业详情（含快照解析）
     */
    public Map<String, Object> getScanJobDetail(Long jobId) {
        DamScanJob job = scanJobMapper.selectById(jobId);
        if (job == null) throw new BusinessException("扫描作业不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job", job);
        if (StrUtil.isNotBlank(job.getSnapshotJson())) {
            try {
                result.put("tables", new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        job.getSnapshotJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {}));
            } catch (Exception e) {
                result.put("tables", Collections.emptyList());
            }
        } else {
            result.put("tables", Collections.emptyList());
        }
        return result;
    }

    // ============================================================
    //  技术元数据落库（扫描结果持久化）
    // ============================================================

    /**
     * 将扫描结果持久化到技术元数据表
     * <p>
     * 落库顺序：metadata_schema → metadata_table → metadata_column
     * 相同数据源重复扫描时，按 (datasource_id, schema_name, table_name) 幂等处理
     *
     * @param dataSourceId 数据源ID
     * @param tables       扫描表列表（含 schemaName/tableName/tableType/remarks/columns）
     * @param schemas      扫描的Schema列表
     * @return 持久化后的表数量
     */
    @Transactional
    public int persistScanResult(Long dataSourceId, List<Map<String, Object>> tables, List<String> schemas) {
        if (dataSourceId == null) return 0;

        // 1. Schema 落库
        Map<String, Long> schemaIdMap = new HashMap<>();
        if (schemas != null) {
            for (String schemaName : schemas) {
                Long schemaId = upsertSchema(dataSourceId, schemaName);
                schemaIdMap.put(schemaName, schemaId);
            }
        }

        // 2. 表与字段落库
        int saved = 0;
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");
            String schemaName = (String) table.get("schemaName");
            String tableType = (String) table.get("tableType");
            String remark = (String) table.get("remarks");
            if (StrUtil.isBlank(tableName)) continue;

            Long schemaId = schemaName != null ? schemaIdMap.get(schemaName) : null;
            // metadata_table.schema_id / metadata_column.schema_id 为 NOT NULL，无法定位时用 0 兑底
            if (schemaId == null) schemaId = 0L;

            // 幂等：按数据源+表名查（metadata_table 无 schema_name 列，按 schema_id 精确匹配；schema_id 上方已兑底为 0L 非空）
            MetadataTable mt = tableMapper.selectOne(
                    new LambdaQueryWrapper<MetadataTable>()
                            .eq(MetadataTable::getDatasourceId, dataSourceId)
                            .eq(MetadataTable::getTableName, tableName)
                            .eq(MetadataTable::getSchemaId, schemaId)
                            .last("LIMIT 1"));
            boolean isNew = mt == null;
            if (isNew) {
                mt = new MetadataTable();
                mt.setDatasourceId(dataSourceId);
                mt.setTableName(tableName);
            }
            mt.setSchemaId(schemaId);
            mt.setSchemaName(schemaName);
            mt.setTableType(StrUtil.isNotBlank(tableType) ? tableType : "TABLE");
            mt.setTableComment(remark);
            mt.setLastSyncTime(LocalDateTime.now());
            if (isNew) {
                java.time.LocalDateTime now = LocalDateTime.now();
                // metadata_table 的 created_time/last_modified/last_sync_time 均为 NOT NULL，必须显式赋值
                mt.setCreatedTime(now);
                mt.setLastModified(now);
                mt.setCreatedAt(now);
                mt.setUpdatedAt(now);
                mt.setCreatedBy("system");
                mt.setUpdatedBy("system");
                tableMapper.insert(mt);
            } else {
                mt.setLastModified(LocalDateTime.now());
                mt.setUpdatedAt(LocalDateTime.now());
                mt.setUpdatedBy("system");
                tableMapper.updateById(mt);
            }
            saved++;

            // 字段落库：先删后插，保证与远程一致
            columnMapper.delete(new LambdaQueryWrapper<MetadataColumn>()
                    .eq(MetadataColumn::getTableId, mt.getId()));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) table.getOrDefault("columns", Collections.emptyList());
            int ordinal = 1;
            for (Map<String, Object> col : cols) {
                MetadataColumn mc = new MetadataColumn();
                mc.setDatasourceId(dataSourceId);
                mc.setSchemaId(schemaId);
                mc.setTableId(mt.getId());
                mc.setColumnName((String) col.get("name"));
                mc.setColumnType((String) col.get("type"));
                if (col.get("size") instanceof Number) {
                    mc.setColumnLength(((Number) col.get("size")).longValue());
                }
                mc.setIsNullable(Boolean.TRUE.equals(col.get("nullable")));
                mc.setIsPrimaryKey(Boolean.TRUE.equals(col.get("isPrimaryKey")));
                mc.setDefaultValue((String) col.get("defaultValue"));
                mc.setColumnComment((String) col.get("comment"));
                mc.setOrdinalPosition(ordinal++);
                java.time.LocalDateTime now = LocalDateTime.now();
                mc.setCreatedAt(now);
                mc.setUpdatedAt(now);
                mc.setCreatedBy("system");
                mc.setUpdatedBy("system");
                columnMapper.insert(mc);
            }
        }
        return saved;
    }

    /**
     * Schema 幂等落库
     */
    private Long upsertSchema(Long dataSourceId, String schemaName) {
        MetadataSchema ms = schemaMapper.selectOne(
                new LambdaQueryWrapper<MetadataSchema>()
                        .eq(MetadataSchema::getDatasourceId, dataSourceId)
                        .eq(MetadataSchema::getSchemaName, schemaName)
                        .last("LIMIT 1"));
        if (ms != null) return ms.getId();
        ms = new MetadataSchema();
        ms.setDatasourceId(dataSourceId);
        ms.setSchemaName(schemaName);
        ms.setSchemaType("DATABASE");
        ms.setStatus(1);
        java.time.LocalDateTime now = LocalDateTime.now();
        ms.setCreatedAt(now);
        ms.setUpdatedAt(now);
        ms.setCreatedBy("system");
        ms.setUpdatedBy("system");
        schemaMapper.insert(ms);
        return ms.getId();
    }

    // ============================================================
    //  技术元数据查询
    // ============================================================

    /**
     * 查询数据源下的技术元数据表列表
     */
    public List<MetadataTable> listTables(Long dataSourceId, String schemaName, String keyword) {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        if (dataSourceId != null) {
            wrapper.eq(MetadataTable::getDatasourceId, dataSourceId);
        }
        if (StrUtil.isNotBlank(schemaName)) {
            // metadata_table 无 schema_name 列，经 metadata_schema 解析 schema_id 后按列过滤
            Long schemaId = resolveSchemaId(dataSourceId, schemaName);
            if (schemaId == null) return Collections.emptyList();
            wrapper.eq(MetadataTable::getSchemaId, schemaId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(MetadataTable::getTableName, keyword)
                    .or().like(MetadataTable::getTableComment, keyword));
        }
        wrapper.orderByDesc(MetadataTable::getLastSyncTime);
        return tableMapper.selectList(wrapper);
    }

    /**
     * 查询数据源下的Schema列表
     */
    public List<MetadataSchema> listSchemas(Long dataSourceId) {
        LambdaQueryWrapper<MetadataSchema> wrapper = new LambdaQueryWrapper<>();
        if (dataSourceId != null) {
            wrapper.eq(MetadataSchema::getDatasourceId, dataSourceId);
        }
        wrapper.orderByAsc(MetadataSchema::getSchemaName);
        return schemaMapper.selectList(wrapper);
    }

    /**
     * 技术元数据表详情（含字段）
     */
    public Map<String, Object> getTableDetail(Long tableId) {
        MetadataTable table = tableMapper.selectById(tableId);
        if (table == null) throw new BusinessException("技术元数据表不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", table);

        List<MetadataColumn> columns = columnMapper.selectList(
                new LambdaQueryWrapper<MetadataColumn>()
                        .eq(MetadataColumn::getTableId, tableId)
                        .orderByAsc(MetadataColumn::getOrdinalPosition));
        result.put("columns", columns);

        // 管理元数据
        MetadataManagement mgmt = managementMapper.selectOne(
                new LambdaQueryWrapper<MetadataManagement>()
                        .eq(MetadataManagement::getTableId, tableId)
                        .last("LIMIT 1"));
        result.put("management", mgmt);
        return result;
    }

    /**
     * 获取某数据源下的全部字段（扁平列表，用于安全扫描等场景）
     */
    public List<MetadataColumn> listColumnsByDataSource(Long dataSourceId) {
        return columnMapper.selectList(
                new LambdaQueryWrapper<MetadataColumn>()
                        .eq(MetadataColumn::getDatasourceId, dataSourceId)
                        .orderByAsc(MetadataColumn::getOrdinalPosition));
    }

    // ============================================================
    //  管理元数据
    // ============================================================

    /**
     * 保存管理元数据（按 table_id 幂等）
     */
    @Transactional
    public MetadataManagement saveManagementMetadata(MetadataManagement mgmt) {
        if (mgmt.getTableId() == null) throw new BusinessException("tableId不能为空");
        MetadataManagement existing = managementMapper.selectOne(
                new LambdaQueryWrapper<MetadataManagement>()
                        .eq(MetadataManagement::getTableId, mgmt.getTableId())
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setGovernanceStatusId(mgmt.getGovernanceStatusId());
            existing.setLifecycleStatusId(mgmt.getLifecycleStatusId());
            existing.setRetentionDays(mgmt.getRetentionDays());
            existing.setSourceSystemId(mgmt.getSourceSystemId());
            existing.setEtlWorkflow(mgmt.getEtlWorkflow());
            existing.setLastAccessedAt(mgmt.getLastAccessedAt());
            existing.setDailyQueryAvg(mgmt.getDailyQueryAvg());
            existing.setSecurityClassification(mgmt.getSecurityClassification());
            existing.setDataVerificationStatus(mgmt.getDataVerificationStatus());
            existing.setRegistrationBatchId(mgmt.getRegistrationBatchId());
            existing.setLastDdlTime(mgmt.getLastDdlTime());
            existing.setAuditRemark(mgmt.getAuditRemark());
            existing.setExtendProps(mgmt.getExtendProps());
            managementMapper.updateById(existing);
            return existing;
        }
        java.time.LocalDateTime now = LocalDateTime.now();
        mgmt.setCreatedAt(now);
        mgmt.setUpdatedAt(now);
        if (mgmt.getCreatedBy() == null) mgmt.setCreatedBy("system");
        if (mgmt.getUpdatedBy() == null) mgmt.setUpdatedBy("system");
        managementMapper.insert(mgmt);
        return mgmt;
    }

    /**
     * 获取数据源名称（供详情展示）
     */
    public String getDataSourceName(Long dataSourceId) {
        if (dataSourceId == null) return null;
        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        return ds != null ? ds.getDsName() : null;
    }

    /**
     * 按表名+数据源 精确查找技术元数据表（供资产详情回填技术元数据ID）
     */
    public MetadataTable findByDataSourceAndTable(Long dataSourceId, String tableName, String schemaName) {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        if (dataSourceId != null) {
            wrapper.eq(MetadataTable::getDatasourceId, dataSourceId);
        }
        if (StrUtil.isNotBlank(schemaName)) {
            Long schemaId = resolveSchemaId(dataSourceId, schemaName);
            if (schemaId == null) return null;
            wrapper.eq(MetadataTable::getSchemaId, schemaId);
        }
        wrapper.eq(MetadataTable::getTableName, tableName).last("LIMIT 1");
        return tableMapper.selectOne(wrapper);
    }

    /**
     * 查询数据源下已落库的技术元数据表（用于前端快速浏览）
     */
    public List<Map<String, Object>> listDataSourceTables(Long dataSourceId, String schemaName, String keyword) {
        List<MetadataTable> tables = listTables(dataSourceId, schemaName, keyword);
        if (tables.isEmpty()) return Collections.emptyList();
        Set<Long> tableIds = tables.stream().map(MetadataTable::getId).collect(Collectors.toSet());
        Map<Long, Long> fieldCountMap = columnMapper.selectList(
                        new LambdaQueryWrapper<MetadataColumn>().in(MetadataColumn::getTableId, tableIds))
                .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId, Collectors.counting()));
        // schema 名称经 schema_id 关联 metadata_schema 批量解析（metadata_table 无 schema_name 列）
        Set<Long> schemaIds = tables.stream().map(MetadataTable::getSchemaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> schemaNameMap = new HashMap<>();
        if (!schemaIds.isEmpty()) {
            for (MetadataSchema s : schemaMapper.selectByIds(schemaIds)) {
                if (s != null) schemaNameMap.put(s.getId(), s.getSchemaName());
            }
        }
        return tables.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("tableName", t.getTableName());
            m.put("schemaName", t.getSchemaId() != null ? schemaNameMap.getOrDefault(t.getSchemaId(), "") : "");
            m.put("tableType", t.getTableType());
            m.put("tableComment", t.getTableComment());
            m.put("fieldCount", fieldCountMap.getOrDefault(t.getId(), 0L));
            m.put("lastSyncTime", t.getLastSyncTime());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 按数据源+Schema名解析 metadata_schema.id（metadata_table 无 schema_name 列，需经此关联过滤）
     */
    private Long resolveSchemaId(Long dataSourceId, String schemaName) {
        if (StrUtil.isBlank(schemaName)) return null;
        MetadataSchema ms = schemaMapper.selectOne(
                new LambdaQueryWrapper<MetadataSchema>()
                        .eq(dataSourceId != null, MetadataSchema::getDatasourceId, dataSourceId)
                        .eq(MetadataSchema::getSchemaName, schemaName)
                        .last("LIMIT 1"));
        return ms != null ? ms.getId() : null;
    }

    // ============================================================
    //  元数据层级概况 / Schema 统计（元数据管理页顶部概况）
    // ============================================================

    /** 元数据层级固定顺序：源库 → ODS → DWD → DWS → ADS → DIM */
    private static final List<String> LAYER_ORDER =
            List.of("SOURCE", "ODS", "DWD", "DWS", "ADS", "DIM");

    /**
     * 按数据源层级（data_layer）聚合规数据源数、表数、字段数
     *
     * @return 每个层级一项：layer/sourceCount/tableCount/columnCount
     */
    public List<Map<String, Object>> getLayerOverview() {
        List<DamDataSource> dsList = dataSourceMapper.selectList(null);

        // 每个数据源的表数
        Map<Long, Long> dsTableCount = new HashMap<>();
        for (MetadataTable t : tableMapper.selectList(null)) {
            if (t.getDatasourceId() == null) continue;
            dsTableCount.merge(t.getDatasourceId(), 1L, Long::sum);
        }
        // 每个数据源的字段数
        Map<Long, Long> dsColumnCount = new HashMap<>();
        for (MetadataColumn c : columnMapper.selectList(null)) {
            if (c.getDatasourceId() == null) continue;
            dsColumnCount.merge(c.getDatasourceId(), 1L, Long::sum);
        }

        // 固定层级顺序初始化
        Map<String, Map<String, Object>> layerAgg = new LinkedHashMap<>();
        for (String layer : LAYER_ORDER) {
            layerAgg.put(layer, newLayerItem(layer));
        }

        for (DamDataSource ds : dsList) {
            String layer = StrUtil.isNotBlank(ds.getDataLayer()) ? ds.getDataLayer() : "SOURCE";
            Map<String, Object> item = layerAgg.computeIfAbsent(layer, this::newLayerItem);
            item.put("sourceCount", (Long) item.get("sourceCount") + 1);
            item.put("tableCount", (Long) item.get("tableCount") + dsTableCount.getOrDefault(ds.getId(), 0L));
            item.put("columnCount", (Long) item.get("columnCount") + dsColumnCount.getOrDefault(ds.getId(), 0L));
        }
        return new ArrayList<>(layerAgg.values());
    }

    /** 构建一个空层级统计项 */
    private Map<String, Object> newLayerItem(String layer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("layer", layer);
        m.put("sourceCount", 0L);
        m.put("tableCount", 0L);
        m.put("columnCount", 0L);
        return m;
    }

    /**
     * 数据源 Schema 统计：每个数据源的 Schema 数量、总表数，以及每个 Schema 的表数量
     *
     * @return [{dataSourceId, schemaCount, tableCount, schemas:[{schemaId,schemaName,tableCount}]}]
     */
    public List<Map<String, Object>> getDataSourceSchemaStats() {
        // 每个 Schema 的表数
        Map<Long, Long> schemaTableCount = new HashMap<>();
        for (MetadataTable t : tableMapper.selectList(null)) {
            if (t.getSchemaId() == null) continue;
            schemaTableCount.merge(t.getSchemaId(), 1L, Long::sum);
        }

        // Schema 按数据源分组
        Map<Long, List<MetadataSchema>> dsSchemas = new LinkedHashMap<>();
        for (MetadataSchema s : schemaMapper.selectList(null)) {
            if (s.getDatasourceId() == null) continue;
            dsSchemas.computeIfAbsent(s.getDatasourceId(), k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<MetadataSchema>> e : dsSchemas.entrySet()) {
            List<Map<String, Object>> schemas = new ArrayList<>();
            long tableTotal = 0;
            for (MetadataSchema s : e.getValue()) {
                long tc = schemaTableCount.getOrDefault(s.getId(), 0L);
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("schemaId", s.getId());
                sm.put("schemaName", s.getSchemaName());
                sm.put("tableCount", tc);
                schemas.add(sm);
                tableTotal += tc;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dataSourceId", e.getKey());
            m.put("schemaCount", schemas.size());
            m.put("tableCount", tableTotal);
            m.put("schemas", schemas);
            result.add(m);
        }
        return result;
    }
}
