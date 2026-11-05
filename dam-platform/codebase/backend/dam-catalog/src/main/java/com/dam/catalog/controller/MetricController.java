package com.dam.catalog.controller;

import com.dam.catalog.entity.MetricDefinition;
import com.dam.catalog.entity.MetricValue;
import com.dam.catalog.service.MetricService;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 指标中心：指标定义、手工填报值和 AI 生成指标 SQL
 */
@RestController
@RequestMapping("/api/catalog/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @GetMapping
    public Result<PageResult<MetricDefinition>> page(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String metricType,
                                                     @RequestParam(required = false) String businessDomain,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(metricService.page(keyword, metricType, businessDomain, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<MetricDefinition> get(@PathVariable Long id) {
        return Result.success(metricService.get(id));
    }

    @PostMapping
    public Result<MetricDefinition> create(@RequestBody Map<String, Object> body) {
        body.remove("id");
        return Result.success(metricService.save(body));
    }

    @PutMapping("/{id}")
    public Result<MetricDefinition> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return Result.success(metricService.save(body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        metricService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/ai-sql")
    public Result<Map<String, Object>> aiSql(@PathVariable Long id,
                                             @RequestHeader(value = "token", required = false) String token) {
        return Result.success(metricService.aiGenerateSql(id, token));
    }

    @GetMapping("/{id}/values")
    public Result<List<MetricValue>> values(@PathVariable Long id) {
        return Result.success(metricService.values(id));
    }

    @PostMapping("/{id}/values")
    public Result<MetricValue> addValue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(metricService.saveValue(id, body));
    }

    @DeleteMapping("/{id}/values/{valueId}")
    public Result<Void> deleteValue(@PathVariable Long id, @PathVariable Long valueId) {
        metricService.deleteValue(id, valueId);
        return Result.success(null);
    }
}
