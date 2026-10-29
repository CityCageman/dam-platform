package com.dam.security.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.security.entity.DamDataAccessRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据访问申请 Mapper（security_access_application 表）
 * <p>
 * 基于 MyBatis-Plus 提供数据访问申请表的基础 CRUD，
 * 由 {@link com.dam.security.service.DataAccessRequestService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamDataAccessRequestMapper extends BaseMapper<DamDataAccessRequest> {
}
