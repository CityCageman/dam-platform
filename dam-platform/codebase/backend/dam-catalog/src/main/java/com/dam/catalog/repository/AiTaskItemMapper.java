package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.AiTaskItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务明细 Mapper
 */
@Mapper
public interface AiTaskItemMapper extends BaseMapper<AiTaskItem> {
}
