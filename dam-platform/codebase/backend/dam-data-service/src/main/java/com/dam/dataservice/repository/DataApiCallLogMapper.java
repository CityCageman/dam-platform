package com.dam.dataservice.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.dataservice.entity.DataApiCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开放 API 调用审计 Mapper（dam_data_service.data_api_call_log）
 * <p>
 * 基于 MyBatis-Plus 提供审计日志的基础写入与查询能力。
 *
 * @author DAM Platform
 */
@Mapper
public interface DataApiCallLogMapper extends BaseMapper<DataApiCallLog> {
}
