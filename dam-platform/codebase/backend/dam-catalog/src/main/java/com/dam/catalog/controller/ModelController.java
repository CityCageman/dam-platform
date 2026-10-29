package com.dam.catalog.controller;

import com.dam.catalog.entity.DamDataModel;
import com.dam.catalog.entity.DamModelField;
import com.dam.catalog.service.ModelService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据模型管理接口（dam_model 模块）
 * <p>
 * 提供模型主题域（model_domain）与模型字段映射（model_column_mapping）的
 * 增删改查、发布/归档，以及模型与资产结构对比能力。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/models")
@RequiredArgsConstructor
public class ModelController {

    /** 数据模型服务 */
    private final ModelService modelService;

    // ==================== 模型接口 ====================

    /**
     * 查询模型列表（支持关键字/分类过滤）
     */
    @GetMapping
    public Result<List<DamDataModel>> getModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return Result.success(modelService.getModels(keyword, categoryId));
    }

    /**
     * 查询模型详情
     */
    @GetMapping("/{id}")
    public Result<DamDataModel> getModelDetail(@PathVariable Long id) {
        return Result.success(modelService.getModelDetail(id));
    }

    /**
     * 创建模型（自动补全审计字段）
     */
    @PostMapping
    public Result<DamDataModel> createModel(@RequestBody DamDataModel model) {
        return Result.success(modelService.createModel(model));
    }

    /**
     * 更新模型
     */
    @PutMapping
    public Result<DamDataModel> updateModel(@RequestBody DamDataModel model) {
        return Result.success(modelService.updateModel(model));
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.success();
    }

    /**
     * 发布模型（状态置为启用）
     */
    @PostMapping("/{id}/publish")
    public Result<DamDataModel> publishModel(@PathVariable Long id) {
        return Result.success(modelService.publishModel(id));
    }

    /**
     * 归档模型（状态置为禁用）
     */
    @PostMapping("/{id}/archive")
    public Result<DamDataModel> archiveModel(@PathVariable Long id) {
        return Result.success(modelService.archiveModel(id));
    }

    // ==================== 模型字段接口 ====================

    /**
     * 查询模型的字段映射列表
     */
    @GetMapping("/{modelId}/fields")
    public Result<List<DamModelField>> getModelFields(@PathVariable Long modelId) {
        return Result.success(modelService.getModelFields(modelId));
    }

    /**
     * 新增模型字段映射（自动关联模型ID）
     */
    @PostMapping("/{modelId}/fields")
    public Result<DamModelField> createModelField(@PathVariable Long modelId, @RequestBody DamModelField field) {
        // 字段归属指定模型
        field.setModelId(modelId);
        return Result.success(modelService.createModelField(field));
    }

    /**
     * 更新模型字段映射
     */
    @PutMapping("/{modelId}/fields")
    public Result<DamModelField> updateModelField(@RequestBody DamModelField field) {
        return Result.success(modelService.updateModelField(field));
    }

    /**
     * 删除模型字段映射
     */
    @DeleteMapping("/fields/{fieldId}")
    public Result<Void> deleteModelField(@PathVariable Long fieldId) {
        modelService.deleteModelField(fieldId);
        return Result.success();
    }

    // ==================== 模型对比 ====================

    /**
     * 获取可参与对比的资产列表
     */
    @GetMapping("/compare/assets")
    public Result<List<Map<String, Object>>> getComparableAssets() {
        return Result.success(modelService.getComparableAssets());
    }

    /**
     * 对比模型与资产的结构差异
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compareModelWithAsset(@RequestBody Map<String, Long> params) {
        Long modelId = params.get("modelId");
        Long assetId = params.get("assetId");
        // 参数校验
        if (modelId == null || assetId == null) {
            return Result.error(400, "请指定模型ID和资产ID");
        }
        return Result.success(modelService.compareModelWithAsset(modelId, assetId));
    }
}
