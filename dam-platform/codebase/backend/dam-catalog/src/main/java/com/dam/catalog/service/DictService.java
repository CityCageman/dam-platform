package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.catalog.entity.SysDictData;
import com.dam.catalog.entity.SysDictType;
import com.dam.catalog.repository.SysDictDataMapper;
import com.dam.catalog.repository.SysDictTypeMapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用字典服务
 * <p>
 * 基于 sys_dict_type / sys_dict_data 两张通用字典表，
 * 对外提供：
 * <ul>
 *   <li>按类型编码查询启用字典项列表（供前端下拉框渲染）</li>
 *   <li>按类型编码查询 value→label 映射（供后端枚举翻译）</li>
 *   <li>字典类型列表及字典项 CRUD（供字典管理维护）</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class DictService {

    /** 字典数据 Mapper（sys_dict_data 表） */
    private final SysDictDataMapper dictDataMapper;

    /** 字典类型 Mapper（sys_dict_type 表） */
    private final SysDictTypeMapper dictTypeMapper;

    /**
     * 按字典类型编码查询该类型下所有启用的字典项（按 sortOrder 升序）
     * <p>
     * 若类型编码不存在则返回空列表（不抛异常，保证前端下拉容错）。
     *
     * @param typeCode 字典类型编码（如 asset_type、ticket_status）
     * @return 启用中的字典数据列表，按排序号升序
     */
    public List<SysDictData> getDictDataByTypeCode(String typeCode) {
        // 1. 先按编码定位字典类型
        SysDictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getDictTypeCode, typeCode));
        // 2. 类型不存在直接返回空列表
        if (type == null) {
            return List.of();
        }
        // 3. 查询该类型下启用中的字典数据，按排序号升序返回
        return dictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictTypeId, type.getId())
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getSortOrder));
    }

    /**
     * 按字典类型编码查询 value→label 映射（用于后端枚举翻译）
     * <p>
     * 例：getDictMap("ticket_status") 返回 {0:待处理, 1:处理中, 2:已完成, 3:已驳回}。
     *
     * @param typeCode 字典类型编码
     * @return LinkedHashMap（保持字典排序），value→label
     */
    public Map<String, String> getDictMap(String typeCode) {
        // 将启用字典项收集为 value→label 的有序映射
        return getDictDataByTypeCode(typeCode).stream()
                .collect(Collectors.toMap(
                        SysDictData::getDictValue,
                        SysDictData::getDictLabel,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * 查询全部字典类型（含禁用，供字典管理界面展示维护）
     *
     * @return 全部字典类型列表，按 id 升序
     */
    public List<SysDictType> getDictTypes() {
        // 管理界面需要看到含禁用的全部类型，以便维护
        return dictTypeMapper.selectList(
                new LambdaQueryWrapper<SysDictType>()
                        .orderByAsc(SysDictType::getId));
    }

    /**
     * 分页查询字典类型（含禁用，供字典管理界面列表分页展示）
     *
     * @param keyword  搜索关键词（按类型编码/类型名称模糊匹配，可空）
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页字典类型
     */
    public PageResult<SysDictType> getDictTypesPage(String keyword, Integer page, Integer pageSize) {
        Page<SysDictType> p = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<SysDictType> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            qw.and(w -> w.like(SysDictType::getDictTypeCode, keyword)
                    .or().like(SysDictType::getDictTypeName, keyword));
        }
        qw.orderByAsc(SysDictType::getId);
        Page<SysDictType> result = dictTypeMapper.selectPage(p, qw);
        return PageResult.of(result);
    }

    /**
     * 按类型编码查询该类型下全部字典数据（含禁用，供字典管理界面展示维护）
     *
     * @param typeCode 字典类型编码
     * @return 全部字典数据，按排序号升序
     */
    public List<SysDictData> getAllDictDataByTypeCode(String typeCode) {
        // 1. 先按编码定位字典类型
        SysDictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getDictTypeCode, typeCode));
        // 2. 类型不存在直接返回空列表
        if (type == null) {
            return List.of();
        }
        // 3. 查询该类型下全部字典数据（含禁用），按排序号升序返回
        return dictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictTypeId, type.getId())
                        .orderByAsc(SysDictData::getSortOrder));
    }

    /**
     * 分页查询指定类型下的字典数据（含禁用，供字典管理界面抽屉展示维护）
     *
     * @param typeCode 字典类型编码
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页字典数据
     */
    public PageResult<SysDictData> getDictDataPage(String typeCode, Integer page, Integer pageSize) {
        SysDictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getDictTypeCode, typeCode));
        if (type == null) {
            return PageResult.of(new Page<>());
        }
        Page<SysDictData> p = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        Page<SysDictData> result = dictDataMapper.selectPage(p, new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, type.getId())
                .orderByAsc(SysDictData::getSortOrder));
        return PageResult.of(result);
    }

    /**
     * 新增字典类型（自动补全审计字段）
     *
     * @param type 字典类型实体（dictTypeCode 必填）
     * @return 创建后的字典类型（含自增ID）
     */
    @Transactional
    public SysDictType createDictType(SysDictType type) {
        // 校验类型编码不能为空
        if (type.getDictTypeCode() == null || type.getDictTypeCode().isBlank()) {
            throw new BusinessException("字典类型编码不能为空");
        }
        // 校验类型编码唯一性（含软删除数据）
        Long dup = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getDictTypeCode, type.getDictTypeCode()));
        if (dup != null && dup > 0) {
            throw new BusinessException("字典类型编码已存在: " + type.getDictTypeCode());
        }
        // 补全审计字段与默认值（sys_dict_type 审计字段非空）
        LocalDateTime now = LocalDateTime.now();
        fillTypeAuditFields(type, now);
        dictTypeMapper.insert(type);
        return type;
    }

    /**
     * 新增字典数据项（自动补全审计字段与排序号）
     *
     * @param data 字典数据实体（dictTypeId、dictLabel、dictValue 必填）
     * @return 创建后的字典数据（含自增ID）
     */
    @Transactional
    public SysDictData createDictData(SysDictData data) {
        // 校验必填字段
        if (data.getDictTypeId() == null) {
            throw new BusinessException("字典类型ID不能为空");
        }
        if (data.getDictLabel() == null || data.getDictLabel().isBlank()) {
            throw new BusinessException("字典标签不能为空");
        }
        if (data.getDictValue() == null || data.getDictValue().isBlank()) {
            throw new BusinessException("字典值不能为空");
        }
        // 补全审计字段与默认值（sys_dict_data 审计字段非空）
        LocalDateTime now = LocalDateTime.now();
        fillDataAuditFields(data, now);
        dictDataMapper.insert(data);
        return data;
    }

    /**
     * 更新字典类型（编码唯一性校验，排除自身）
     *
     * @param type 字典类型实体（id 必填）
     * @return 更新后的字典类型
     */
    @Transactional
    public SysDictType updateDictType(SysDictType type) {
        if (type.getId() == null) {
            throw new BusinessException("字典类型ID不能为空");
        }
        SysDictType existing = dictTypeMapper.selectById(type.getId());
        if (existing == null) {
            throw new BusinessException("字典类型不存在");
        }
        // 编码唯一性校验（排除自身）
        if (type.getDictTypeCode() != null && !type.getDictTypeCode().isBlank()) {
            SysDictType dup = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                    .eq(SysDictType::getDictTypeCode, type.getDictTypeCode())
                    .ne(SysDictType::getId, type.getId())
                    .last("LIMIT 1"));
            if (dup != null) {
                throw new BusinessException("字典类型编码已存在: " + type.getDictTypeCode());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        type.setUpdatedAt(now);
        type.setUpdatedBy(type.getUpdatedBy() == null ? "system" : type.getUpdatedBy());
        dictTypeMapper.updateById(type);
        return dictTypeMapper.selectById(type.getId());
    }

    /**
     * 删除字典类型（级联删除该类型下的全部字典数据）
     *
     * @param id 字典类型ID
     */
    @Transactional
    public void deleteDictType(Long id) {
        SysDictType existing = dictTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("字典类型不存在");
        }
        // 级联删除该类型下的字典数据
        dictDataMapper.delete(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeId, id));
        dictTypeMapper.deleteById(id);
    }

    /**
     * 更新字典数据项
     *
     * @param data 字典数据实体（id 必填）
     * @return 更新后的字典数据
     */
    @Transactional
    public SysDictData updateDictData(SysDictData data) {
        if (data.getId() == null) {
            throw new BusinessException("字典数据ID不能为空");
        }
        SysDictData existing = dictDataMapper.selectById(data.getId());
        if (existing == null) {
            throw new BusinessException("字典数据不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        data.setUpdatedAt(now);
        data.setUpdatedBy(data.getUpdatedBy() == null ? "system" : data.getUpdatedBy());
        dictDataMapper.updateById(data);
        return dictDataMapper.selectById(data.getId());
    }

    /**
     * 删除字典数据项（逻辑删除，软删除标记 deleted_at）
     *
     * @param id 字典数据ID
     */
    @Transactional
    public void deleteDictData(Long id) {
        // 先校验记录存在
        SysDictData existing = dictDataMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("字典数据不存在");
        }
        dictDataMapper.deleteById(id);
    }

    /** 补全字典类型创建时的审计字段与默认值 */
    private void fillTypeAuditFields(SysDictType type, LocalDateTime now) {
        type.setStatus(type.getStatus() == null ? 1 : type.getStatus());
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        type.setCreatedBy(type.getCreatedBy() == null ? "system" : type.getCreatedBy());
        type.setUpdatedBy(type.getUpdatedBy() == null ? "system" : type.getUpdatedBy());
    }

    /** 补全字典数据创建时的审计字段与默认值 */
    private void fillDataAuditFields(SysDictData data, LocalDateTime now) {
        data.setStatus(data.getStatus() == null ? 1 : data.getStatus());
        data.setSortOrder(data.getSortOrder() == null ? 0 : data.getSortOrder());
        data.setIsDefault(data.getIsDefault() == null ? Boolean.FALSE : data.getIsDefault());
        data.setCreatedAt(now);
        data.setUpdatedAt(now);
        data.setCreatedBy(data.getCreatedBy() == null ? "system" : data.getCreatedBy());
        data.setUpdatedBy(data.getUpdatedBy() == null ? "system" : data.getUpdatedBy());
    }
}
