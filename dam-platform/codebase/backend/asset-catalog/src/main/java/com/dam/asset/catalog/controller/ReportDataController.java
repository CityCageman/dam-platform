package com.dam.asset.catalog.controller;

import com.dam.asset.metadata.entity.DamDataSource;
import com.dam.asset.metadata.repository.DamDataSourceMapper;
import com.dam.asset.catalog.entity.DamAsset;
import com.dam.asset.catalog.repository.DamAssetMapper;
import com.dam.platform.common.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 报表服务使用的 DAM 只读数据接口，避免报表直接依赖 DAM Mapper。 */
@RestController
@RequestMapping("/api/catalog/report-data")
public class ReportDataController {

    private final DamDataSourceMapper dataSourceMapper;
    private final DamAssetMapper assetMapper;

    public ReportDataController(DamDataSourceMapper dataSourceMapper, DamAssetMapper assetMapper) {
        this.dataSourceMapper = dataSourceMapper;
        this.assetMapper = assetMapper;
    }

    @GetMapping("/data-source/{id}")
    public Result<Map<String, Object>> dataSource(@PathVariable Long id) {
        DamDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) return Result.error("数据源不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ds.getId());
        m.put("dsName", ds.getDsName());
        m.put("dsType", ds.getDsType());
        m.put("jdbcUrl", ds.getJdbcUrl());
        m.put("username", ds.getUsername());
        m.put("password", ds.getPassword());
        m.put("status", ds.getStatus());
        return Result.success(m);
    }

    @GetMapping("/asset-comments")
    public Result<Map<String, String>> assetComments(@RequestParam Long dataSourceId) {
        List<DamAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<DamAsset>()
                .eq(DamAsset::getDataSourceId, dataSourceId)
                .isNotNull(DamAsset::getDescription));
        return Result.success(assets.stream().filter(a -> a.getDescription() != null)
                .collect(Collectors.toMap(DamAsset::getAssetName, DamAsset::getDescription, (a, b) -> a)));
    }
}
