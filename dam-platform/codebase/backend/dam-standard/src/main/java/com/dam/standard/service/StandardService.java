package com.dam.standard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.standard.entity.DamStandard;
import com.dam.standard.entity.DamStandardMapping;
import com.dam.standard.entity.DamDeviationReport;
import com.dam.standard.repository.DamDeviationReportMapper;
import com.dam.standard.entity.DamStandardValue;
import com.dam.standard.repository.DamStandardMapper;
import com.dam.standard.repository.DamStandardMappingMapper;
import com.dam.standard.repository.DamStandardValueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据标准服务
 * <p>
 * 负责标准码表（standard_code_table）与码值（standard_code_value）的维护、
 * 标准发布/审批/归档、标准映射（standard_mapping）与偏差报告（standard_deviation_report）处理。
 * 注意：新表标准码表字段为 code_table_code/code_table_name/code_table_type，
 * 旧字段 stdCode/stdName/stdType 已废弃。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class StandardService {

    /** 标准码表 Mapper */
    private final DamStandardMapper standardMapper;

    /** 标准码值 Mapper */
    private final DamStandardValueMapper standardValueMapper;

    /** 标准映射 Mapper */
    private final DamStandardMappingMapper standardMappingMapper;

    /** 偏差报告 Mapper */
    private final DamDeviationReportMapper deviationReportMapper;

    /**
     * 分页检索标准码表（支持关键字/类型/状态过滤）
     *
     * @param keyword  关键字（模糊匹配码表名称或编码）
     * @param stdType  码表类型
     * @param status   状态（数字字符串）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<DamStandard> searchStandards(String keyword, String stdType, String status,
                                                    Integer page, Integer pageSize) {
        // 构造分页对象
        Page<DamStandard> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamStandard> wrapper = new LambdaQueryWrapper<>();
        // 关键字：码表名称 OR 码表编码（用 and 包裹避免 or 扩散到后续条件）
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(DamStandard::getCodeTableName, keyword)
                    .or().like(DamStandard::getCodeTableCode, keyword));
        }
        // 按码表类型过滤
        if (StrUtil.isNotBlank(stdType)) {
            wrapper.eq(DamStandard::getCodeTableType, stdType);
        }
        // 按状态过滤
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(DamStandard::getStatus, Integer.parseInt(status));
        }
        // 按创建时间倒序
        wrapper.orderByDesc(DamStandard::getCreatedAt);
        PageResult<DamStandard> result = PageResult.of(standardMapper.selectPage(p, wrapper));
        // 填充前端兼容字段 stdName/stdCode/stdType
        for (DamStandard s : result.getRecords()) {
            fillStandardCompat(s);
        }
        return result;
    }

    /**
     * 填充标准前端兼容字段（stdName/stdCode/stdType ← codeTable* 真实列）
     *
     * @param std 标准实体（兼容字段不持久化）
     */
    private void fillStandardCompat(DamStandard std) {
        if (std == null) return;
        if (std.getStdName() == null) std.setStdName(std.getCodeTableName());
        if (std.getStdCode() == null) std.setStdCode(std.getCodeTableCode());
        if (std.getStdType() == null) std.setStdType(std.getCodeTableType());
    }

    /**
     * 查询标准码表详情（返回前填充前端兼容字段）
     *
     * @param id 标准ID
     * @return 标准实体
     */
    public DamStandard getStandardDetail(Long id) {
        // 查询并校验存在
        DamStandard std = standardMapper.selectById(id);
        if (std == null) throw new BusinessException("标准不存在");
        fillStandardCompat(std);
        return std;
    }

    /**
     * 创建标准码表（自动补全审计字段，默认状态禁用）
     * <p>
     * 兼容前端字段：stdName→codeTableName、stdCode→codeTableCode、stdType→codeTableType。
     *
     * @param standard 标准实体
     * @return 创建后的标准
     */
    @Transactional
    public DamStandard createStandard(DamStandard standard) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(standard.getCodeTableName()) && StrUtil.isNotBlank(standard.getStdName())) {
            standard.setCodeTableName(standard.getStdName());
        }
        if (StrUtil.isBlank(standard.getCodeTableCode()) && StrUtil.isNotBlank(standard.getStdCode())) {
            standard.setCodeTableCode(standard.getStdCode());
        }
        if (StrUtil.isBlank(standard.getCodeTableType()) && StrUtil.isNotBlank(standard.getStdType())) {
            standard.setCodeTableType(standard.getStdType());
        }
        // 默认状态：0-禁用（待审批）
        if (standard.getStatus() == null) {
            standard.setStatus(0);
        }
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        standard.setCreatedAt(now);
        standard.setUpdatedAt(now);
        if (standard.getCreatedBy() == null) standard.setCreatedBy("system");
        if (standard.getUpdatedBy() == null) standard.setUpdatedBy("system");
        standardMapper.insert(standard);
        return standard;
    }

    /**
     * 更新标准码表（按主键更新非空字段，兼容前端旧字段名）
     */
    @Transactional
    public DamStandard updateStandard(DamStandard standard) {
        // 兼容前端旧字段名
        if (StrUtil.isBlank(standard.getCodeTableName()) && StrUtil.isNotBlank(standard.getStdName())) {
            standard.setCodeTableName(standard.getStdName());
        }
        if (StrUtil.isBlank(standard.getCodeTableCode()) && StrUtil.isNotBlank(standard.getStdCode())) {
            standard.setCodeTableCode(standard.getStdCode());
        }
        if (StrUtil.isBlank(standard.getCodeTableType()) && StrUtil.isNotBlank(standard.getStdType())) {
            standard.setCodeTableType(standard.getStdType());
        }
        standardMapper.updateById(standard);
        return standard;
    }

    /**
     * 删除标准码表（级联删除关联码值与映射）
     *
     * @param id 标准ID
     */
    @Transactional
    public void deleteStandard(Long id) {
        // 删除关联的值域
        standardValueMapper.delete(new LambdaQueryWrapper<DamStandardValue>().eq(DamStandardValue::getCodeTableId, id));
        // 删除关联的映射
        standardMappingMapper.delete(new LambdaQueryWrapper<DamStandardMapping>().eq(DamStandardMapping::getStandardCodeTableId, id));
        // 删除标准本体
        standardMapper.deleteById(id);
    }

    /**
     * 查询标准码表的码值列表（按排序号升序）
     *
     * @param stdId 标准ID
     * @return 码值列表
     */
    public List<DamStandardValue> getStandardValues(Long stdId) {
        LambdaQueryWrapper<DamStandardValue> wrapper = new LambdaQueryWrapper<>();
        // 按码表ID过滤
        wrapper.eq(DamStandardValue::getCodeTableId, stdId);
        // 按排序号升序
        wrapper.orderByAsc(DamStandardValue::getSortOrder);
        return standardValueMapper.selectList(wrapper);
    }

    /**
     * 新增标准码值（自动补全审计字段）
     *
     * @param value 码值实体
     * @return 创建后的码值
     */
    @Transactional
    public DamStandardValue createStandardValue(DamStandardValue value) {
        // 默认状态启用
        if (value.getStatus() == null) value.setStatus(1);
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        if (value.getCreatedBy() == null) value.setCreatedBy("system");
        if (value.getUpdatedBy() == null) value.setUpdatedBy("system");
        standardValueMapper.insert(value);
        return value;
    }

    // ==================== 标准发布审批 ====================

    /**
     * 发布标准（状态置为启用）
     */
    @Transactional
    public DamStandard publishStandard(Long id) {
        // 查询并校验存在
        DamStandard std = standardMapper.selectById(id);
        if (std == null) throw new BusinessException("标准不存在");
        std.setStatus(1);
        standardMapper.updateById(std);
        return std;
    }

    /**
     * 审批标准（approved=true 启用，否则禁用）
     */
    @Transactional
    public DamStandard approveStandard(Long id, boolean approved) {
        // 查询并校验存在
        DamStandard std = standardMapper.selectById(id);
        if (std == null) throw new BusinessException("标准不存在");
        std.setStatus(approved ? 1 : 0);
        standardMapper.updateById(std);
        return std;
    }

    /**
     * 归档标准（状态置为禁用）
     */
    @Transactional
    public DamStandard archiveStandard(Long id) {
        // 查询并校验存在
        DamStandard std = standardMapper.selectById(id);
        if (std == null) throw new BusinessException("标准不存在");
        std.setStatus(0);
        standardMapper.updateById(std);
        return std;
    }

    /**
     * 查询标准码表的映射列表
     *
     * @param stdId 标准ID
     * @return 映射列表
     */
    public List<DamStandardMapping> getMappings(Long stdId) {
        return standardMappingMapper.selectList(
                new LambdaQueryWrapper<DamStandardMapping>().eq(DamStandardMapping::getStandardCodeTableId, stdId));
    }

    /**
     * 新增标准映射（自动补全审计字段）
     *
     * @param mapping 映射实体
     * @return 创建后的映射
     */
    @Transactional
    public DamStandardMapping createMapping(DamStandardMapping mapping) {
        // 默认状态启用
        if (mapping.getStatus() == null) {
            mapping.setStatus(1);
        }
        // 补全审计字段
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        if (mapping.getCreatedBy() == null) mapping.setCreatedBy("system");
        if (mapping.getUpdatedBy() == null) mapping.setUpdatedBy("system");
        standardMappingMapper.insert(mapping);
        return mapping;
    }

    /**
     * 删除标准映射
     */
    @Transactional
    public void removeMapping(Long id) {
        standardMappingMapper.deleteById(id);
    }

    /**
     * 获取标准覆盖统计（标准数/映射数/覆盖率）
     *
     * @return 统计 Map
     */
    public Map<String, Object> getCoverageStats() {
        // 统计标准数与映射数
        long totalStandards = standardMapper.selectCount(null);
        long totalMappings = standardMappingMapper.selectCount(null);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStandards", totalStandards);
        stats.put("totalMappings", totalMappings);
        // 覆盖率 = 映射数 / 标准数（百分比，保留两位）
        stats.put("coverageRate", totalStandards > 0 ?
                Math.round((double) totalMappings / totalStandards * 10000) / 100.0 : 0);
        return stats;
    }

    // ==================== 偏差报告 ====================

    /**
     * 查询标准偏差报告（按状态/严重程度过滤，按检查时间倒序）
     *
     * @param status   状态（数字字符串）
     * @param severity 严重程度（HIGH/MEDIUM/LOW）
     * @return 偏差报告列表
     */
    public List<DamDeviationReport> getDeviationReports(String status, String severity) {
        LambdaQueryWrapper<DamDeviationReport> wrapper = new LambdaQueryWrapper<>();
        // 按状态过滤
        if (StrUtil.isNotBlank(status)) wrapper.eq(DamDeviationReport::getStatus, Integer.parseInt(status));
        // 按严重程度过滤
        if (StrUtil.isNotBlank(severity)) wrapper.eq(DamDeviationReport::getSeverity, severity);
        // 按检查时间倒序
        wrapper.orderByDesc(DamDeviationReport::getCheckTime);
        return deviationReportMapper.selectList(wrapper);
    }

    /**
     * 处理标准偏差（标记已修复并记录修复时间）
     *
     * @param id 偏差报告ID
     */
    public void resolveDeviation(Long id) {
        // 查询偏差报告
        DamDeviationReport report = deviationReportMapper.selectById(id);
        if (report != null) {
            // 标记为已修复并记录修复时间
            report.setStatus(1);
            report.setFixedAt(LocalDateTime.now());
            deviationReportMapper.updateById(report);
        }
    }
}
