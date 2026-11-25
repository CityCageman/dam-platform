package com.dam.service.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.service.api.entity.DataApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据 API Mapper（dam_dsp.data_api）
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
    @Select("SELECT COUNT(1) FROM dam_dsp.data_api WHERE api_code = #{apiCode}")
    long countByApiCodeIncludingDeleted(@Param("apiCode") String apiCode);

    /**
     * 按 apiCode 查询未逻辑删除的数据 API
     * <p>
     * 自定义 SQL 不会自动追加 {@code @TableLogic} 条件，这里显式限定
     * {@code deleted_at IS NULL}；apiCode 为数据库唯一键，最多返回一条。
     * 调用方仍需自行校验 {@code status=PUBLISHED} 等运行条件。
     *
     * @param apiCode API 编码
     * @return 数据 API 定义；不存在或已删除时返回 null
     */
    @Select("SELECT * FROM dam_dsp.data_api "
            + "WHERE api_code = #{apiCode} AND deleted_at IS NULL LIMIT 1")
    DataApi selectByApiCode(@Param("apiCode") String apiCode);
}
