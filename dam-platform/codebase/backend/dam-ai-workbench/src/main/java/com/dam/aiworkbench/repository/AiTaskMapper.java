package com.dam.aiworkbench.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.aiworkbench.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务 Mapper
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {
}
