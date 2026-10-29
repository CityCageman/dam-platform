package com.dam.catalog.controller;

import com.dam.catalog.service.ReportCatalogService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报表体系接口（报表即资产）
 * <p>
 * 提供维度树、卡片分页、详情、维护与在线执行能力；
 * 数据可见性复用「角色 → 主题域」授权。
 */
@RestController
@RequestMapping("/api/catalog/reports")
@RequiredArgsConstructor
public class ReportCatalogController {

    private final ReportCatalogService reportCatalogService;

    /**
     * 报表维度树（维度 + 维度值，按当前角色可见主题域过滤）
     */
    @GetMapping("/dimensions")
    public Result<List<Map<String, Object>>> dimensions() {
        return Result.success(reportCatalogService.listDimensions());
    }

    /**
     * 报表卡片分页
     *
     * @param dimensionCode  维度编码（SUBJECT_DOMAIN / REPORT_TYPE / UPDATE_FREQ）
     * @param dimensionValue 维度值
     */
    @GetMapping("/cards")
    public Result<Map<String, Object>> cards(@RequestParam(required = false) String dimensionCode,
                                             @RequestParam(required = false) String dimensionValue,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(reportCatalogService.pageCards(dimensionCode, dimensionValue, keyword, page, pageSize));
    }

    /**
     * 报表详情
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(reportCatalogService.getDetail(id));
    }

    /**
     * 新建报表（同时创建报表资产）
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        body.remove("id");
        return Result.success(reportCatalogService.save(body));
    }

    /**
     * 更新报表
     */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return Result.success(reportCatalogService.save(body));
    }

    /**
     * 删除报表（逻辑删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportCatalogService.delete(id);
        return Result.success();
    }

    /**
     * 在线执行报表（SQL / 存储过程），SQL 中用 :paramName 绑定参数
     */
    @PostMapping("/{id}/run")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> run(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("params");
        Map<String, Object> params = raw instanceof Map ? (Map<String, Object>) raw : Map.of();
        return Result.success(reportCatalogService.run(id, params));
    }
}
