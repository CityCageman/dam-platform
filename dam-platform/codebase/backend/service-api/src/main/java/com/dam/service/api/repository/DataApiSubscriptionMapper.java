package com.dam.service.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.service.api.entity.DataApiSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据 API 订阅 Mapper（dam_dsp.api_subscription）
 * <p>
 * 基于 MyBatis-Plus 提供订阅关系的基础 CRUD。
 *
 * @author DAM Platform
 */
@Mapper
public interface DataApiSubscriptionMapper extends BaseMapper<DataApiSubscription> {
}
