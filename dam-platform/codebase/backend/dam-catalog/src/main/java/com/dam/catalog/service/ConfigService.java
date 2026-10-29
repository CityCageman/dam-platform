package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamConfig;
import com.dam.catalog.repository.DamConfigMapper;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

/**
 * 系统配置服务（sys_config 表）
 * <p>
 * 提供配置项（键值对 + 分组）的查询与管理，
 * 支持按配置类型获取启用列表/全量列表/code→name 映射/code→排序号映射；
 * 配置中心、导航、安全等级等均基于本服务承载。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    /** 系统配置 Mapper（sys_config 表） */
    private final DamConfigMapper configMapper;

    /**
     * 获取指定配置类型的所有启用项
     */
    public List<DamConfig> getConfigByType(String configType) {
        return configMapper.selectList(
                new LambdaQueryWrapper<DamConfig>()
                        .eq(DamConfig::getConfigType, configType)
                        .eq(DamConfig::getStatus, 1)
                        .orderByAsc(DamConfig::getSortOrder));
    }

    /**
     * 获取指定配置类型的所有项（含禁用），用于配置中心管理
     */
    public List<DamConfig> getConfigTree(String configType) {
        return configMapper.selectList(
                new LambdaQueryWrapper<DamConfig>()
                        .eq(DamConfig::getConfigType, configType)
                        .orderByAsc(DamConfig::getSortOrder));
    }

    /**
     * 获取指定配置类型的 code->name 映射
     */
    public Map<String, String> getConfigMap(String configType) {
        List<DamConfig> list = getConfigByType(configType);
        return list.stream().collect(Collectors.toMap(
                DamConfig::getConfigCode, DamConfig::getConfigName, (a, b) -> a));
    }

    /**
     * 获取指定配置类型的 code->sortOrder 优先级映射
     */
    public Map<String, Integer> getConfigPriority(String configType) {
        List<DamConfig> list = getConfigByType(configType);
        return list.stream().collect(Collectors.toMap(
                DamConfig::getConfigCode, DamConfig::getSortOrder, (a, b) -> a));
    }

    @Transactional
    public DamConfig createConfig(DamConfig config) {
        if (config.getStatus() == null) config.setStatus(1);
        if (config.getSortOrder() == null) config.setSortOrder(0);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        if (config.getCreatedBy() == null) config.setCreatedBy("system");
        if (config.getUpdatedBy() == null) config.setUpdatedBy("system");
        configMapper.insert(config);
        return config;
    }

    @Transactional
    public DamConfig updateConfig(DamConfig config) {
        DamConfig existing = configMapper.selectById(config.getId());
        if (existing == null) throw new BusinessException("配置项不存在");
        configMapper.updateById(config);
        return config;
    }

    @Transactional
    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
    }
}
