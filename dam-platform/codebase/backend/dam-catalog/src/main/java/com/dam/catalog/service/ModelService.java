package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamDataModel;
import com.dam.catalog.entity.DamField;
import com.dam.catalog.entity.DamModelField;
import com.dam.catalog.repository.DamDataModelMapper;
import com.dam.catalog.repository.DamFieldMapper;
import com.dam.catalog.repository.DamModelFieldMapper;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据模型服务
 * <p>
 * 负责模型主题域（model_domain）与模型字段映射（model_column_mapping）的
 * 增删改查、发布/归档，以及模型与实际资产字段结构的差异对比。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class ModelService {

    /** 模型 Mapper（model_domain 表） */
    private final DamDataModelMapper dataModelMapper;

    /** 模型字段映射 Mapper（model_column_mapping 表） */
    private final DamModelFieldMapper modelFieldMapper;

    /** 字段 Mapper（metadata_column 表） */
    private final DamFieldMapper fieldMapper;

    /** 资产 Mapper */
    private final DamAssetMapper assetMapper;

    // ==================== 模型管理 ====================

    /**
     * 查询模型列表（支持关键字过滤）
     */
    public List<DamDataModel> getModels(String keyword, Long categoryId) {
        LambdaQueryWrapper<DamDataModel> wrapper = new LambdaQueryWrapper<>();
        // 关键字模糊匹配主题域名称/编码
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(DamDataModel::getDomainName, keyword)
                   .or().like(DamDataModel::getDomainCode, keyword));
        }
        // 按创建时间倒序
        wrapper.orderByDesc(DamDataModel::getCreatedAt);
        List<DamDataModel> list = dataModelMapper.selectList(wrapper);
        // 填充前端兼容字段 modelName/modelCode
        for (DamDataModel m : list) {
            fillModelCompat(m);
        }
        return list;
    }

    /**
     * 填充模型前端兼容字段（modelName/modelCode ← domain* 真实列）
     *
     * @param m 模型实体（兼容字段不持久化）
     */
    private void fillModelCompat(DamDataModel m) {
        if (m == null) return;
        if (m.getModelName() == null) m.setModelName(m.getDomainName());
        if (m.getModelCode() == null) m.setModelCode(m.getDomainCode());
    }

    /**
     * 查询模型详情（返回前填充前端兼容字段）
     */
    public DamDataModel getModelDetail(Long id) {
        // 查询并校验存在
        DamDataModel model = dataModelMapper.selectById(id);
        if (model == null) throw new BusinessException("模型不存在");
        fillModelCompat(model);
        return model;
    }

    /**
     * 创建模型（自动补全审计字段与默认值，兼容前端旧字段名）
     * <p>
     * 兼容映射：modelName→domainName、modelCode→domainCode。
     */
    @Transactional
    public DamDataModel createModel(DamDataModel model) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(model.getDomainName()) && StrUtil.isNotBlank(model.getModelName())) {
            model.setDomainName(model.getModelName());
        }
        if (StrUtil.isBlank(model.getDomainCode()) && StrUtil.isNotBlank(model.getModelCode())) {
            model.setDomainCode(model.getModelCode());
        }
        // 校验主题域名称非空
        if (StrUtil.isBlank(model.getDomainName())) {
            throw new BusinessException("模型名称不能为空");
        }
        // 默认排序号与启用状态
        if (model.getSortOrder() == null) model.setSortOrder(0);
        if (model.getStatus() == null) model.setStatus(1);
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        if (model.getCreatedBy() == null) model.setCreatedBy("system");
        if (model.getUpdatedBy() == null) model.setUpdatedBy("system");
        dataModelMapper.insert(model);
        return model;
    }

    /**
     * 更新模型（仅更新传入的非空字段，兼容前端旧字段名）
     */
    @Transactional
    public DamDataModel updateModel(DamDataModel model) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(model.getDomainName()) && StrUtil.isNotBlank(model.getModelName())) {
            model.setDomainName(model.getModelName());
        }
        if (StrUtil.isBlank(model.getDomainCode()) && StrUtil.isNotBlank(model.getModelCode())) {
            model.setDomainCode(model.getModelCode());
        }
        // 查询并校验存在
        DamDataModel existing = dataModelMapper.selectById(model.getId());
        if (existing == null) throw new BusinessException("模型不存在");
        // 仅更新传入的非空字段
        if (StrUtil.isNotBlank(model.getDomainName())) {
            existing.setDomainName(model.getDomainName());
        }
        if (StrUtil.isNotBlank(model.getDomainCode())) {
            existing.setDomainCode(model.getDomainCode());
        }
        if (model.getDescription() != null) {
            existing.setDescription(model.getDescription());
        }
        if (model.getParentId() != null) {
            existing.setParentId(model.getParentId());
        }
        dataModelMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除模型（级联删除关联字段映射）
     */
    @Transactional
    public void deleteModel(Long id) {
        // 查询并校验存在
        DamDataModel model = dataModelMapper.selectById(id);
        if (model == null) throw new BusinessException("模型不存在");
        // 同时删除关联的字段
        modelFieldMapper.delete(new LambdaQueryWrapper<DamModelField>().eq(DamModelField::getModelId, id));
        dataModelMapper.deleteById(id);
    }

    /**
     * 发布模型（状态置为启用）
     */
    @Transactional
    public DamDataModel publishModel(Long id) {
        // 查询并校验存在
        DamDataModel model = dataModelMapper.selectById(id);
        if (model == null) throw new BusinessException("模型不存在");
        model.setStatus(1);
        dataModelMapper.updateById(model);
        return model;
    }

    /**
     * 归档模型（状态置为禁用）
     */
    @Transactional
    public DamDataModel archiveModel(Long id) {
        // 查询并校验存在
        DamDataModel model = dataModelMapper.selectById(id);
        if (model == null) throw new BusinessException("模型不存在");
        model.setStatus(0);
        dataModelMapper.updateById(model);
        return model;
    }

    // ==================== 模型字段管理 ====================

    /**
     * 查询模型的字段映射列表（按排序号升序，返回前填充前端兼容字段）
     */
    public List<DamModelField> getModelFields(Long modelId) {
        LambdaQueryWrapper<DamModelField> wrapper = new LambdaQueryWrapper<>();
        // 按模型ID过滤
        wrapper.eq(DamModelField::getModelId, modelId);
        wrapper.orderByAsc(DamModelField::getSortOrder);
        List<DamModelField> list = modelFieldMapper.selectList(wrapper);
        // 填充前端兼容字段
        for (DamModelField f : list) {
            fillModelFieldCompat(f);
        }
        return list;
    }

    /**
     * 填充模型字段前端兼容字段（fieldName/dataType/dataLength/isNullable ← targetColumn* 真实列）
     *
     * @param f 模型字段实体（兼容字段不持久化）
     */
    private void fillModelFieldCompat(DamModelField f) {
        if (f == null) return;
        if (f.getFieldName() == null) f.setFieldName(f.getTargetColumnName());
        if (f.getDataType() == null) f.setDataType(f.getTargetColumnType());
        if (f.getDataLength() == null) f.setDataLength(0);
        if (f.getIsNullable() == null) f.setIsNullable(1);
    }

    /**
     * 新增模型字段映射（自动补全审计字段，兼容前端旧字段名）
     * <p>
     * 兼容映射：fieldName→targetColumnName、dataType→targetColumnType。
     */
    @Transactional
    public DamModelField createModelField(DamModelField field) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(field.getTargetColumnName()) && StrUtil.isNotBlank(field.getFieldName())) {
            field.setTargetColumnName(field.getFieldName());
        }
        if (StrUtil.isBlank(field.getTargetColumnType()) && StrUtil.isNotBlank(field.getDataType())) {
            field.setTargetColumnType(field.getDataType());
        }
        // 校验目标字段名非空
        if (StrUtil.isBlank(field.getTargetColumnName())) {
            throw new BusinessException("目标字段名不能为空");
        }
        // 默认排序号与启用状态
        if (field.getSortOrder() == null) field.setSortOrder(0);
        if (field.getStatus() == null) field.setStatus(1);
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        field.setCreatedAt(now);
        field.setUpdatedAt(now);
        if (field.getCreatedBy() == null) field.setCreatedBy("system");
        if (field.getUpdatedBy() == null) field.setUpdatedBy("system");
        modelFieldMapper.insert(field);
        return field;
    }

    /**
     * 更新模型字段映射（仅更新传入的非空字段，兼容前端旧字段名）
     */
    @Transactional
    public DamModelField updateModelField(DamModelField field) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(field.getTargetColumnName()) && StrUtil.isNotBlank(field.getFieldName())) {
            field.setTargetColumnName(field.getFieldName());
        }
        if (StrUtil.isBlank(field.getTargetColumnType()) && StrUtil.isNotBlank(field.getDataType())) {
            field.setTargetColumnType(field.getDataType());
        }
        // 查询并校验存在
        DamModelField existing = modelFieldMapper.selectById(field.getId());
        if (existing == null) throw new BusinessException("字段不存在");
        // 仅更新传入的非空字段
        if (StrUtil.isNotBlank(field.getTargetColumnName())) existing.setTargetColumnName(field.getTargetColumnName());
        if (StrUtil.isNotBlank(field.getTargetColumnType())) existing.setTargetColumnType(field.getTargetColumnType());
        if (StrUtil.isNotBlank(field.getModelType())) existing.setModelType(field.getModelType());
        if (field.getSourceTableId() != null) existing.setSourceTableId(field.getSourceTableId());
        if (field.getSourceColumnId() != null) existing.setSourceColumnId(field.getSourceColumnId());
        if (field.getTransformRule() != null) existing.setTransformRule(field.getTransformRule());
        if (field.getSortOrder() != null) existing.setSortOrder(field.getSortOrder());
        modelFieldMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除模型字段映射
     */
    @Transactional
    public void deleteModelField(Long fieldId) {
        modelFieldMapper.deleteById(fieldId);
    }

    // ==================== 模型与资产字段对比 ====================

    /**
     * 对比模型字段与实际资产字段，返回差异明细
     */
    public Map<String, Object> compareModelWithAsset(Long modelId, Long assetId) {
        // 查询模型与资产并校验存在
        DamDataModel model = dataModelMapper.selectById(modelId);
        if (model == null) throw new BusinessException("模型不存在");
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");

        List<DamModelField> modelFields = getModelFields(modelId);
        // 资产字段通过多态外键（source_metadata_id=metadata_table.id）查询
        List<DamField> assetFields = asset.getSourceMetadataId() != null ? fieldMapper.selectList(
                new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()))
                : Collections.emptyList();

        // 以模型目标字段名为key建立索引
        Map<String, DamModelField> modelFieldMap = modelFields.stream()
                .collect(Collectors.toMap(DamModelField::getTargetColumnName, f -> f, (a, b) -> a));
        Map<String, DamField> assetFieldMap = assetFields.stream()
                .collect(Collectors.toMap(DamField::getFieldName, f -> f, (a, b) -> a));

        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> extra = new ArrayList<>();
        List<Map<String, Object>> mismatched = new ArrayList<>();

        // 遍历模型字段，检查资产中是否存在及是否一致
        for (DamModelField mf : modelFields) {
            String mfName = mf.getTargetColumnName();
            DamField af = assetFieldMap.get(mfName);
            if (af == null) {
                missing.add(Map.of(
                        "fieldName", mfName,
                        "expectedType", mf.getTargetColumnType()
                ));
                continue;
            }
            // 检查类型是否一致
            List<Map<String, Object>> diffs = new ArrayList<>();
            if (mf.getTargetColumnType() != null
                    && !Objects.equals(mf.getTargetColumnType(), af.getDataType())) {
                diffs.add(Map.of("property", "数据类型", "expected", mf.getTargetColumnType(), "actual", af.getDataType()));
            }
            if (!diffs.isEmpty()) {
                mismatched.add(Map.of(
                        "fieldName", mfName,
                        "differences", diffs
                ));
            } else {
                matched.add(Map.of("fieldName", mfName));
            }
        }

        // 检查资产中多余的字段（不在模型中的）
        for (DamField af : assetFields) {
            if (!modelFieldMap.containsKey(af.getFieldName())) {
                extra.add(Map.of(
                        "fieldName", af.getFieldName(),
                        "dataType", af.getDataType()
                ));
            }
        }

        int totalModelFields = modelFields.size();
        int totalAssetFields = assetFields.size();
        int matchedCount = matched.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelName", model.getDomainName());
        result.put("assetName", asset.getAssetName());
        result.put("summary", Map.of(
                "totalModelFields", totalModelFields,
                "totalAssetFields", totalAssetFields,
                "matched", matchedCount,
                "missing", missing.size(),
                "extra", extra.size(),
                "mismatched", mismatched.size(),
                "complianceRate", totalModelFields > 0
                        ? Math.round((double) matchedCount / totalModelFields * 10000) / 100.0
                        : 0
        ));
        result.put("matched", matched);
        result.put("missing", missing);
        result.put("extra", extra);
        result.put("mismatched", mismatched);
        return result;
    }

    /**
     * 获取可供对比的资产列表（有字段的资产）
     */
    public List<Map<String, Object>> getComparableAssets() {
        // 新表 asset_info 无 table_name 列，资产名(asset_name)承载表名语义（后续仍按字段数>0 过滤）
        List<DamAsset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>().isNotNull(DamAsset::getAssetName));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DamAsset asset : assets) {
            if (asset.getSourceMetadataId() == null) continue;
            long fieldCount = fieldMapper.selectCount(
                    new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
            if (fieldCount > 0) {
                result.add(Map.of(
                        "id", asset.getId(),
                        "assetName", asset.getAssetName(),
                        "tableName", asset.getTableName(),
                        "fieldCount", fieldCount
                ));
            }
        }
        return result;
    }
}
