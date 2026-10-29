package com.dam.lifecycle.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lifecycle.entity.DamDestructionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据销毁记录 Mapper（metadata_destruction_record 表）
 * <p>
 * 基于 MyBatis-Plus 提供数据销毁记录表的基础 CRUD，
 * 由 {@link com.dam.lifecycle.service.LifecycleService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamDestructionRecordMapper extends BaseMapper<DamDestructionRecord> {
}
