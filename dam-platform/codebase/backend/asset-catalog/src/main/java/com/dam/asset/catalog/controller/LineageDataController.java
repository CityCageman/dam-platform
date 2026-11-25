package com.dam.asset.catalog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.asset.metadata.entity.DamSourceSystem;
import com.dam.asset.metadata.entity.MetadataColumn;
import com.dam.asset.metadata.entity.MetadataTable;
import com.dam.asset.metadata.repository.DamSourceSystemMapper;
import com.dam.asset.metadata.repository.MetadataColumnMapper;
import com.dam.asset.metadata.repository.MetadataTableMapper;
import com.dam.platform.common.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 血缘只读数据接口；DGP 不再直接查询 DAM schema。 */
@RestController
@RequestMapping("/api/catalog/lineage-data")
public class LineageDataController {

    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final DamSourceSystemMapper sourceSystemMapper;

    public LineageDataController(MetadataTableMapper tableMapper,
                                 MetadataColumnMapper columnMapper,
                                 DamSourceSystemMapper sourceSystemMapper) {
        this.tableMapper = tableMapper;
        this.columnMapper = columnMapper;
        this.sourceSystemMapper = sourceSystemMapper;
    }

    /** 表名 -> 层级/Schema；数据源缺失时返回空字段，由 DGP 用表名前缀兜底 */
    @GetMapping("/table-layers")
    public Result<List<Map<String, Object>>> tableLayers(@RequestParam List<String> names) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : names == null ? List.<String>of() : names) {
            MetadataTable t = tableMapper.selectOne(new LambdaQueryWrapper<MetadataTable>()
                    .eq(MetadataTable::getTableName, name).last("LIMIT 1"));
            if (t == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("table_name", t.getTableName());
            m.put("data_layer", null);
            m.put("schema_name", null);
            out.add(m);
        }
        return Result.success(out);
    }

    /** 当前实现由 DGP 血缘服务自行降级，保留接口供后续扩展全文检索 */
    @GetMapping("/search-columns")
    public Result<List<Map<String, Object>>> searchColumns(@RequestParam String keyword,
                                                           @RequestParam(defaultValue = "20") int limit) {
        List<MetadataColumn> columns = columnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                .and(w -> w.like(MetadataColumn::getColumnComment, keyword).or().like(MetadataColumn::getColumnName, keyword))
                .eq(MetadataColumn::getStatus, 1)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MetadataColumn c : columns) {
            MetadataTable t = tableMapper.selectById(c.getTableId());
            if (t == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("column_id", c.getId());
            m.put("column_name", c.getColumnName());
            m.put("column_comment", c.getColumnComment());
            m.put("column_type", c.getColumnType());
            m.put("table_id", t.getId());
            m.put("table_name", t.getTableName());
            m.put("table_comment", t.getTableComment());
            out.add(m);
        }
        return Result.success(out);
    }

    @GetMapping("/search-tables")
    public Result<List<Map<String, Object>>> searchTables(@RequestParam String keyword,
                                                          @RequestParam(defaultValue = "20") int limit) {
        List<MetadataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<MetadataTable>()
                .and(w -> w.like(MetadataTable::getTableComment, keyword).or().like(MetadataTable::getTableName, keyword))
                .eq(MetadataTable::getStatus, 1)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MetadataTable t : tables) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("table_id", t.getId());
            m.put("table_name", t.getTableName());
            m.put("table_comment", t.getTableComment());
            out.add(m);
        }
        return Result.success(out);
    }

    @GetMapping("/source-systems")
    public Result<List<Map<String, Object>>> sourceSystems(@RequestParam(required = false) List<Long> ids) {
        List<DamSourceSystem> list = ids == null || ids.isEmpty()
                ? sourceSystemMapper.selectList(null)
                : sourceSystemMapper.selectList(new LambdaQueryWrapper<DamSourceSystem>().in(DamSourceSystem::getId, ids));
        List<Map<String, Object>> out = new ArrayList<>();
        for (DamSourceSystem s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("systemCode", s.getSystemCode());
            m.put("systemName", s.getSystemName());
            out.add(m);
        }
        return Result.success(out);
    }
}
