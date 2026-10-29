package com.dam.standard.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.standard.entity.DamDeviationReport;
import com.dam.standard.entity.DamStandard;
import com.dam.standard.entity.DamStandardMapping;
import com.dam.standard.entity.DamStandardValue;
import com.dam.standard.service.StandardAutoValidatorService;
import com.dam.standard.service.StandardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据标准接口
 * <p>
 * 提供：
 * <ul>
 *   <li>标准码表（standard_code_table）的检索/详情/增改删</li>
 *   <li>码值（standard_code_value）与标准映射（standard_mapping）维护</li>
 *   <li>标准发布/审批/归档</li>
 *   <li>自动校验与偏差报告（standard_deviation_report）处理</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/standard")
@RequiredArgsConstructor
public class StandardController {

    /** 数据标准服务 */
    private final StandardService standardService;

    /** 标准自动校验服务 */
    private final StandardAutoValidatorService autoValidatorService;

    /**
     * 分页检索标准码表（支持关键字/类型/状态过滤）
     */
    @GetMapping("/search")
    public Result<PageResult<DamStandard>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stdType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(standardService.searchStandards(keyword, stdType, status, page, pageSize));
    }

    /**
     * 查询标准码表详情
     */
    @GetMapping("/{id}")
    public Result<DamStandard> detail(@PathVariable Long id) {
        return Result.success(standardService.getStandardDetail(id));
    }

    /**
     * 创建标准码表（自动补全审计字段）
     */
    @PostMapping
    public Result<DamStandard> create(@RequestBody DamStandard standard) {
        return Result.success(standardService.createStandard(standard));
    }

    /**
     * 更新标准码表
     */
    @PutMapping("/{id}")
    public Result<DamStandard> update(@PathVariable Long id, @RequestBody DamStandard standard) {
        // 路径ID优先于请求体ID
        standard.setId(id);
        return Result.success(standardService.updateStandard(standard));
    }

    /**
     * 删除标准码表（级联删除关联码值与映射）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        standardService.deleteStandard(id);
        return Result.success();
    }

    /**
     * 查询标准码表的码值列表
     */
    @GetMapping("/{stdId}/values")
    public Result<List<DamStandardValue>> getValues(@PathVariable Long stdId) {
        return Result.success(standardService.getStandardValues(stdId));
    }

    /**
     * 新增标准码值（自动补全审计字段）
     */
    @PostMapping("/values")
    public Result<DamStandardValue> createValue(@RequestBody DamStandardValue value) {
        return Result.success(standardService.createStandardValue(value));
    }

    // ==================== 发布审批 ====================

    /**
     * 发布标准（状态置为启用）
     */
    @PostMapping("/{id}/publish")
    public Result<DamStandard> publish(@PathVariable Long id) {
        return Result.success(standardService.publishStandard(id));
    }

    /**
     * 审批标准（approved=true 启用，否则禁用）
     */
    @PostMapping("/{id}/approve")
    public Result<DamStandard> approve(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return Result.success(standardService.approveStandard(id, body.getOrDefault("approved", true)));
    }

    /**
     * 归档标准（状态置为禁用）
     */
    @PostMapping("/{id}/archive")
    public Result<DamStandard> archive(@PathVariable Long id) {
        return Result.success(standardService.archiveStandard(id));
    }

    // ==================== 标准映射 ====================

    /**
     * 查询标准码表的映射列表
     */
    @GetMapping("/{stdId}/mappings")
    public Result<List<DamStandardMapping>> getMappings(@PathVariable Long stdId) {
        return Result.success(standardService.getMappings(stdId));
    }

    /**
     * 新增标准映射（自动补全审计字段）
     */
    @PostMapping("/mappings")
    public Result<DamStandardMapping> createMapping(@RequestBody DamStandardMapping mapping) {
        return Result.success(standardService.createMapping(mapping));
    }

    /**
     * 删除标准映射
     */
    @DeleteMapping("/mappings/{id}")
    public Result<Void> removeMapping(@PathVariable Long id) {
        standardService.removeMapping(id);
        return Result.success();
    }

    /**
     * 获取标准覆盖统计（标准数/映射数/覆盖率）
     */
    @GetMapping("/coverage")
    public Result<Map<String, Object>> getCoverage() {
        return Result.success(standardService.getCoverageStats());
    }

    // ==================== 自动校验 ====================

    /**
     * 执行一次标准自动校验（扫描资产与标准码表差异，生成偏差报告）
     */
    @PostMapping("/auto-validate")
    public Result<Map<String, Object>> autoValidate() {
        int count = autoValidatorService.executeAutoValidation();
        return Result.success(Map.of("deviationCount", count, "message", "校验完成"));
    }

    /**
     * 查询标准偏差报告列表（按状态/严重程度过滤）
     */
    @GetMapping("/deviations")
    public Result<List<DamDeviationReport>> getDeviations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return Result.success(standardService.getDeviationReports(status, severity));
    }

    /**
     * 处理标准偏差（标记已修复并记录修复时间）
     */
    @PutMapping("/deviations/{id}/resolve")
    public Result<Void> resolveDeviation(@PathVariable Long id) {
        standardService.resolveDeviation(id);
        return Result.success();
    }
}
