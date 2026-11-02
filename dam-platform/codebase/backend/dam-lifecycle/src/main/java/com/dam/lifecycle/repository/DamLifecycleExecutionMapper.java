package com.dam.lifecycle.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lifecycle.entity.DamLifecycleExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 生命周期执行记录 Mapper
 */
@Mapper
public interface DamLifecycleExecutionMapper extends BaseMapper<DamLifecycleExecution> {

    /**
     * 判断执行记录表是否已迁移（未迁移时跳过写入，避免拖垮主流程）
     */
    @Select("SELECT to_regclass('dam_metadata.metadata_lifecycle_execution')::text")
    String tableExists();
}
