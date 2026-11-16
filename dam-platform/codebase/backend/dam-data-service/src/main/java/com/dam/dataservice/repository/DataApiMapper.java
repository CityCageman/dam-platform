package com.dam.dataservice.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.dataservice.entity.DataApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据 API Mapper（dam_data_service.data_api）
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；逻辑删除条件由 {@code @TableLogic} 自动追加。
 * 额外提供包含已逻辑删除记录的唯一性统计，避免复用已删除的 api_code 触发
 * 数据库唯一约束异常。
 *
 * @author DAM Platform
 */
@Mapper
public interface DataApiMapper extends BaseMapper<DataApi> {

    /**
     * 统计指定 apiCode 的记录数（包含已逻辑删除的记录）
     *
     * @param apiCode API 编码
     * @return 记录数
     */
    @Select("SELECT COUNT(1) FROM dam_data_service.data_api WHERE api_code = #{apiCode}")
    long countByApiCodeIncludingDeleted(@Param("apiCode") String apiCode);
}
