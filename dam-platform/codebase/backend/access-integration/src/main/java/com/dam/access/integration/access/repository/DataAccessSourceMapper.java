package com.dam.access.integration.access.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.access.integration.access.entity.DataAccessSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据接入数据源 Mapper
 * <p>
 * 写操作复用 MyBatis-Plus {@link BaseMapper}（含 {@code @TableLogic} 逻辑删除）；
 * 读操作使用显式列清单，其中 {@code data_layer} 通过 {@code to_jsonb} 读取，
 * 兼容存量表尚未新增该列的环境（列缺失时返回 NULL，不影响列表/详情）。
 *
 * @author DAM Platform
 */
@Mapper
public interface DataAccessSourceMapper extends BaseMapper<DataAccessSource> {

    /** 数据源显式列清单（data_layer 兼容可选列） */
    String SELECT_COLUMNS = "t.id, t.ds_name, t.ds_code, t.ds_type, t.ds_adapter, "
            + "t.ds_ip, t.ds_port, t.jdbc_url, t.username, t.password, t.driver_class, "
            + "t.connection_props, t.description, t.status, "
            + "(to_jsonb(t)->>'data_layer') AS data_layer, "
            + "t.created_at, t.created_by, t.updated_at, t.updated_by, t.deleted_at";

    /**
     * 查询未删除的数据源列表
     *
     * @param keyword    关键字（名称/编码/URL 模糊匹配），可为空
     * @param dsType     数据源类型编码（忽略大小写精确匹配），可为空
     * @param dsTypeName 数据源类型展示名（兼容历史展示名数据），可为空
     * @param status     状态 0/1，可为空
     * @return 数据源列表（按创建时间升序）
     */
    @Select("<script>"
            + "SELECT " + SELECT_COLUMNS + " "
            + "FROM dam_dap.metadata_datasource t "
            + "WHERE t.deleted_at IS NULL "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (t.ds_name ILIKE '%' || #{keyword} || '%' "
            + "OR t.ds_code ILIKE '%' || #{keyword} || '%' "
            + "OR t.jdbc_url ILIKE '%' || #{keyword} || '%') "
            + "</if>"
            + "<if test='dsType != null and dsType != \"\"'>"
            + "AND upper(t.ds_type) IN (upper(#{dsType}), upper(#{dsTypeName})) "
            + "</if>"
            + "<if test='status != null'>"
            + "AND t.status = #{status} "
            + "</if>"
            + "ORDER BY t.created_at ASC, t.id ASC"
            + "</script>")
    List<DataAccessSource> selectActiveList(@Param("keyword") String keyword,
                                            @Param("dsType") String dsType,
                                            @Param("dsTypeName") String dsTypeName,
                                            @Param("status") Integer status);

    /**
     * 按 ID 查询未删除的数据源
     *
     * @param id 数据源 ID
     * @return 数据源；不存在或已删除时返回 null
     */
    @Select("SELECT " + SELECT_COLUMNS + " "
            + "FROM dam_dap.metadata_datasource t "
            + "WHERE t.id = #{id} AND t.deleted_at IS NULL")
    DataAccessSource selectActiveById(@Param("id") Long id);

    /**
     * 统计未删除数据源中指定编码的数量
     *
     * @param dsCode 数据源代码
     * @return 数量
     */
    @Select("SELECT COUNT(1) FROM dam_dap.metadata_datasource t "
            + "WHERE t.deleted_at IS NULL AND lower(t.ds_code) = lower(#{dsCode})")
    long countByCode(@Param("dsCode") String dsCode);

    /**
     * 统计未删除数据源中指定编码的数量（排除指定 ID，用于更新）
     *
     * @param dsCode    数据源代码
     * @param excludeId 需要排除的数据源 ID
     * @return 数量
     */
    @Select("SELECT COUNT(1) FROM dam_dap.metadata_datasource t "
            + "WHERE t.deleted_at IS NULL AND lower(t.ds_code) = lower(#{dsCode}) "
            + "AND t.id <> #{excludeId}")
    long countByCodeExcludingId(@Param("dsCode") String dsCode,
                                @Param("excludeId") Long excludeId);

    /**
     * 检查 dam_dap.metadata_datasource 是否存在 data_layer 列
     * <p>存量表可能没有该列，写操作在列缺失时忽略 dataLayer，避免强制改表。
     *
     * @return 1 表示存在，0 表示不存在
     */
    @Select("SELECT COUNT(1) FROM information_schema.columns "
            + "WHERE table_schema = 'dam_dap' AND table_name = 'metadata_datasource' "
            + "AND column_name = 'data_layer'")
    long countDataLayerColumn();
}
