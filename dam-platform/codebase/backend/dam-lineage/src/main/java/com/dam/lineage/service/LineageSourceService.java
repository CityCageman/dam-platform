package com.dam.lineage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.vo.PageResult;
import com.dam.lineage.entity.MetadataLineageSource;
import com.dam.lineage.entity.SourceSystem;
import com.dam.lineage.repository.MetadataLineageSourceMapper;
import com.dam.lineage.repository.SourceSystemMapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 血缘节点注册服务
 * <p>
 * 统一管理血缘中涉及的各类节点（报表/视图/存储过程/数据表/基础字典表/ETL作业）。
 * 节点与资产不强关联，source_code 唯一标识；SQL 解析时未注册的节点自动注册（upsert）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineageSourceService {

    private final MetadataLineageSourceMapper sourceMapper;
    /** 来源系统 Mapper（列表回填"所属系统"名称） */
    private final SourceSystemMapper sourceSystemMapper;

    /**
     * 按编码查询节点
     *
     * @param code 节点编码（表名/报表编码/作业名）
     * @return 节点（不存在返回 null）
     */
    public MetadataLineageSource getByCode(String code) {
        if (StrUtil.isBlank(code)) return null;
        return sourceMapper.selectOne(
                new LambdaQueryWrapper<MetadataLineageSource>()
                        .eq(MetadataLineageSource::getSourceCode, code)
                        .last("LIMIT 1"));
    }

    /**
     * 按ID查询节点
     */
    public MetadataLineageSource getById(Long id) {
        return id == null ? null : sourceMapper.selectById(id);
    }

    /**
     * 按ID批量查询节点（用于字段级血缘等场景回填节点名称）
     *
     * @param ids 节点ID集合
     * @return 节点列表（不存在/为空返回空列表）
     */
    public List<MetadataLineageSource> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return sourceMapper.selectByIds(ids);
    }

    /**
     * 注册节点（存在则返回已有记录，不存在则创建）
     * 用于 SQL 解析/血缘采集时自动注册未注册的节点
     *
     * @param code      节点编码（唯一）
     * @param name      节点名称
     * @param typeId    节点类型（lineage_node_type 字典值）
     * @return 节点
     */
    @Transactional
    public MetadataLineageSource upsert(String code, String name, String typeId) {
        return upsert(code, name, typeId, null, null, null, null, null, null);
    }

    /**
     * 注册节点（完整参数版）
     *
     * @param code          节点编码（唯一）
     * @param name          节点名称
     * @param typeId        节点类型（lineage_node_type 字典值）
     * @param path          节点路径（可空）
     * @param content       节点内容（可空）
     * @param owner         负责人（可空）
     * @param sourceSystemId 来源系统ID（可空）
     * @param categoryId    资产分类ID（可空）
     * @param description   描述（可空）
     * @return 节点
     */
    @Transactional
    public MetadataLineageSource upsert(String code, String name, String typeId, String path,
                                        String content, String owner, Long sourceSystemId,
                                        Long categoryId, String description) {
        if (StrUtil.isBlank(code)) throw new IllegalArgumentException("节点编码不能为空");
        MetadataLineageSource existing = getByCode(code);
        if (existing != null) {
            return existing;
        }
        MetadataLineageSource source = new MetadataLineageSource();
        source.setSourceCode(code);
        source.setSourceName(StrUtil.isNotBlank(name) ? name : code);
        source.setSourceTypeId(StrUtil.isNotBlank(typeId) ? typeId : "TABLE");
        source.setSourcePath(path);
        source.setSourceContent(content);
        source.setSourceOwner(owner);
        source.setSourceSystemId(sourceSystemId);
        source.setCategoryId(categoryId);
        source.setDescription(description);
        LocalDateTime now = LocalDateTime.now();
        source.setStatus(1);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        source.setCreatedBy("system");
        source.setUpdatedBy("system");
        sourceMapper.insert(source);
        return source;
    }

    /**
     * 新增节点（手动注册）
     */
    @Transactional
    public MetadataLineageSource create(MetadataLineageSource source) {
        if (StrUtil.isBlank(source.getSourceCode())) {
            throw new IllegalArgumentException("节点编码不能为空");
        }
        if (getByCode(source.getSourceCode()) != null) {
            throw new IllegalArgumentException("节点编码已存在: " + source.getSourceCode());
        }
        LocalDateTime now = LocalDateTime.now();
        source.setStatus(source.getStatus() == null ? 1 : source.getStatus());
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        source.setCreatedBy(StrUtil.isBlank(source.getCreatedBy()) ? "system" : source.getCreatedBy());
        source.setUpdatedBy(StrUtil.isBlank(source.getUpdatedBy()) ? "system" : source.getUpdatedBy());
        sourceMapper.insert(source);
        return source;
    }

    /**
     * 更新节点
     */
    @Transactional
    public MetadataLineageSource update(MetadataLineageSource source) {
        if (source.getId() == null) throw new IllegalArgumentException("节点ID不能为空");
        source.setUpdatedAt(LocalDateTime.now());
        source.setUpdatedBy("system");
        sourceMapper.updateById(source);
        return sourceMapper.selectById(source.getId());
    }

    /**
     * 删除节点（软删除）
     */
    @Transactional
    public void delete(Long id) {
        MetadataLineageSource source = sourceMapper.selectById(id);
        if (source == null) return;
        source.setDeletedAt(LocalDateTime.now());
        sourceMapper.updateById(source);
    }

    /**
     * 分页查询节点
     *
     * @param typeId      节点类型（可空）
     * @param keyword     关键字（按编码/名称模糊，可空）
     * @param categoryIds 资产分类ID集合（可空，空集合表示不过滤）
     * @param page        页码
     * @param pageSize    每页大小
     * @return 分页结果
     */
    public PageResult<MetadataLineageSource> list(String typeId, String keyword, List<Long> categoryIds,
                                                  Integer page, Integer pageSize) {
        Page<MetadataLineageSource> p = new Page<>(page == null ? 1 : page, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<MetadataLineageSource> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(typeId)) {
            wrapper.eq(MetadataLineageSource::getSourceTypeId, typeId);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(MetadataLineageSource::getCategoryId, categoryIds);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(MetadataLineageSource::getSourceCode, keyword)
                    .or().like(MetadataLineageSource::getSourceName, keyword));
        }
        wrapper.orderByDesc(MetadataLineageSource::getCreatedAt);
        PageResult<MetadataLineageSource> result = PageResult.of(sourceMapper.selectPage(p, wrapper));
        fillSourceSystemName(result.getRecords());
        return result;
    }

    /** 批量回填节点"所属系统"名称（sourceSystemId → dam_metadata.source_system.system_name） */
    private void fillSourceSystemName(List<MetadataLineageSource> records) {
        if (records == null || records.isEmpty()) return;
        java.util.Set<Long> sysIds = new java.util.HashSet<>();
        for (MetadataLineageSource s : records) {
            if (s.getSourceSystemId() != null) sysIds.add(s.getSourceSystemId());
        }
        if (sysIds.isEmpty()) return;
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        for (SourceSystem sys : sourceSystemMapper.selectList(
                new LambdaQueryWrapper<SourceSystem>().in(SourceSystem::getId, sysIds))) {
            nameMap.put(sys.getId(), sys.getSystemName());
        }
        for (MetadataLineageSource s : records) {
            s.setSourceSystemName(nameMap.get(s.getSourceSystemId()));
        }
    }

    /**
     * 批量按编码查询节点（用于批量解析表名）
     *
     * @param codes 编码集合
     * @return 节点列表
     */
    public List<MetadataLineageSource> listByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        return sourceMapper.selectList(
                new LambdaQueryWrapper<MetadataLineageSource>().in(MetadataLineageSource::getSourceCode, codes));
    }
}
