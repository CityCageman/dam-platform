package com.dam.aiworkbench.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.aiworkbench.entity.AiTaskItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务明细 Mapper
 */
@Mapper
public interface AiTaskItemMapper extends BaseMapper<AiTaskItem> {
}
