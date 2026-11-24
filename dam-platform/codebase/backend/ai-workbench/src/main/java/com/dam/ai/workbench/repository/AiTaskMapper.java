package com.dam.ai.workbench.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.ai.workbench.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务 Mapper
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {
}
