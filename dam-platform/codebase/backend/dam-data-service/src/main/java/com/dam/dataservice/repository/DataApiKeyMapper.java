package com.dam.dataservice.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.dataservice.entity.DataApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 数据服务 API Key Mapper（dam_data_service.data_api_key）
 * <p>
 * 基于 MyBatis-Plus 提供基础 CRUD；额外提供按 SHA-256 摘要查询与最近使用时间
 * 更新。查询条件只使用摘要，任何日志与响应都不应输出明文 Key 或摘要。
 *
 * @author DAM Platform
 */
@Mapper
public interface DataApiKeyMapper extends BaseMapper<DataApiKey> {

    /**
     * 按 SHA-256 摘要查询 API Key
     *
     * @param keyHash Key 的 SHA-256 十六进制摘要
     * @return API Key 记录；不存在时返回 null
     */
    @Select("SELECT * FROM dam_data_service.data_api_key "
            + "WHERE key_hash = #{keyHash} LIMIT 1")
    DataApiKey selectByKeyHash(@Param("keyHash") String keyHash);

    /**
     * 更新最近使用时间（best-effort，失败不影响 API 响应）
     *
     * @param id         主键
     * @param lastUsedAt 最近使用时间
     * @return 受影响行数
     */
    @Update("UPDATE dam_data_service.data_api_key "
            + "SET last_used_at = #{lastUsedAt} WHERE id = #{id}")
    int updateLastUsedAt(@Param("id") Long id,
                         @Param("lastUsedAt") LocalDateTime lastUsedAt);
}
