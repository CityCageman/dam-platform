package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.ReportCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportCatalogMapper extends BaseMapper<ReportCatalog> {

    /** 报表体系维度定义（可扩展） */
    @Select("SELECT id, dim_code AS \"dimCode\", dim_name AS \"dimName\", source_type AS \"sourceType\", "
            + "source_ref AS \"sourceRef\" FROM dam_asset.report_dimension "
            + "WHERE status = 1 AND deleted_at IS NULL ORDER BY sort_order, id")
    List<Map<String, Object>> selectDimensions();

    /** 静态维度值 */
    @Select("SELECT item_code AS \"itemCode\", item_name AS \"itemName\" FROM dam_asset.report_dimension_item "
            + "WHERE dimension_id = #{dimensionId} AND status = 1 AND deleted_at IS NULL ORDER BY sort_order, id")
    List<Map<String, Object>> selectDimensionItems(@Param("dimensionId") Long dimensionId);
}
