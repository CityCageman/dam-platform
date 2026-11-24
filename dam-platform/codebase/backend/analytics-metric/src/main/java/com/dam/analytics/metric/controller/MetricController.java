package com.dam.analytics.metric.controller;

import com.dam.platform.common.vo.PageResult;
import com.dam.platform.common.vo.Result;
import com.dam.analytics.metric.entity.MetricDefinition;
import com.dam.analytics.metric.entity.MetricValue;
import com.dam.analytics.metric.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标中心 API：指标定义、手工填报值、AI 生成指标 SQL。
 * <p>
 * 所有接口统一前缀 {@code /api/metrics}，由独立的 dam-metric 服务提供；catalog 不再暴露
 * {@code /api/catalog/metrics}。指标定义/指标值表归 dam-metric 独占。
 * <p>
 * 额外提供两组内部只读接口给 catalog：
 * <ul>
 *   <li>{@code GET /api/metrics/internal/active?limit=500}</li>
 *   <li>{@code GET /api/metrics/internal/by-data-source/{dataSourceId}}</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricController {

    /** 指标领域服务 */
    private final MetricService metricService;

    /**
     * 指标服务运行状态。
     *
     * @return 服务名、运行状态、角色、所属 schema 与端口
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-metric");
        status.put("status", "UP");
        status.put("role", "metric center");
        status.put("schema", "dam_metric");
        status.put("port", 8094);
        return Result.success(status);
    }

    /**
     * 分页查询指标定义。
     *
     * @param keyword        关键字（指标名称/编码/定义）
     * @param metricType     指标类型（MANUAL/COMPUTED），可空
     * @param businessDomain 业务域，可空
     * @param status         状态（0-停用，1-启用），可空
     * @param page           页码，从 1 开始
     * @param pageSize       每页条数
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<MetricDefinition>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) String businessDomain,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(
                metricService.page(keyword, metricType, businessDomain, status, page, pageSize));
    }

    /**
     * 查询指标详情。
     *
     * @param id 指标 ID
     * @return 指标定义
     */
    @GetMapping("/{id:\\d+}")
    public Result<MetricDefinition> get(@PathVariable Long id) {
        return Result.success(metricService.get(id));
    }

    /**
     * 新增指标。
     *
     * @param body 指标字段（id 会被忽略）
     * @return 保存后的指标定义
     */
    @PostMapping
    public Result<MetricDefinition> create(@RequestBody Map<String, Object> body) {
        body.remove("id");
        return Result.success(metricService.save(body));
    }

    /**
     * 更新指标。
     *
     * @param id   指标 ID
     * @param body 指标字段
     * @return 保存后的指标定义
     */
    @PutMapping("/{id:\\d+}")
    public Result<MetricDefinition> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return Result.success(metricService.save(body));
    }

    /**
     * 逻辑删除指标。
     *
     * @param id 指标 ID
     * @return 空结果
     */
    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        metricService.delete(id);
        return Result.success(null);
    }

    /**
     * 调用 catalog 内部 AI 接口生成指标 SQL，并写回指标。
     *
     * @param id    指标 ID
     * @param token 当前登录 token，原样透传给 catalog
     * @return SQL、使用表、参数与最新版本号
     */
    @PostMapping("/{id:\\d+}/ai-sql")
    public Result<Map<String, Object>> aiSql(@PathVariable Long id,
                                             @RequestHeader(value = "token", required = false) String token) {
        return Result.success(metricService.aiGenerateSql(id, token));
    }

    /**
     * 查询指标值列表。
     *
     * @param id 指标 ID
     * @return 指标值列表
     */
    @GetMapping("/{id:\\d+}/values")
    public Result<List<MetricValue>> values(@PathVariable Long id) {
        return Result.success(metricService.values(id));
    }

    /**
     * 新增/更新手工填报指标值。
     *
     * @param id   指标 ID
     * @param body 指标值字段
     * @return 保存后的指标值
     */
    @PostMapping("/{id:\\d+}/values")
    public Result<MetricValue> addValue(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(metricService.saveValue(id, body));
    }

    /**
     * 逻辑删除指标值。
     *
     * @param id      指标 ID
     * @param valueId 指标值 ID
     * @return 空结果
     */
    @DeleteMapping("/{id:\\d+}/values/{valueId:\\d+}")
    public Result<Void> deleteValue(@PathVariable Long id, @PathVariable Long valueId) {
        metricService.deleteValue(id, valueId);
        return Result.success(null);
    }

    /**
     * 内部只读接口：查询未删除指标，供 catalog 知识库/报表读取。
     *
     * @param limit 最大条数，默认 500，最大 500
     * @return 指标列表
     */
    @GetMapping("/internal/active")
    public Result<List<MetricDefinition>> internalActive(
            @RequestParam(defaultValue = "500") Integer limit) {
        return Result.success(metricService.listActive(limit));
    }

    /**
     * 内部只读接口：查询指定数据源下启用的指标，供 catalog 报表读取。
     *
     * @param dataSourceId 数据源 ID
     * @return 指标列表
     */
    @GetMapping("/internal/by-data-source/{dataSourceId:\\d+}")
    public Result<List<MetricDefinition>> internalByDataSource(@PathVariable Long dataSourceId) {
        return Result.success(metricService.listActiveByDataSource(dataSourceId));
    }
}
