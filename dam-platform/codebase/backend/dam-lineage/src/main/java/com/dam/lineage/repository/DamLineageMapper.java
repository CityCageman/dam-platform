package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.DamLineage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 数据血缘 Mapper
 * <p>
 * 适配新表 dam_metadata.metadata_lineage：
 * 血缘节点通过 source_type/source_id、target_type/target_id 表达（节点类型统一为 TABLE），
 * source_id / target_id 即资产ID（表级血缘场景）。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamLineageMapper extends BaseMapper<DamLineage> {

    /**
     * 查询指定资产的全部直接下游节点ID（以该资产为源节点的血缘目标）
     *
     * @param assetId 源资产ID
     * @return 下游节点ID列表
     */
    @Select("SELECT DISTINCT target_id FROM metadata_lineage WHERE source_type = 'TABLE' AND source_id = #{assetId}")
    List<Long> findDownstreamAssetIds(Long assetId);

    /**
     * 查询指定资产的全部直接上游节点ID（以该资产为目标节点的血缘来源）
     *
     * @param assetId 目标资产ID
     * @return 上游节点ID列表
     */
    @Select("SELECT DISTINCT source_id FROM metadata_lineage WHERE target_type = 'TABLE' AND target_id = #{assetId}")
    List<Long> findUpstreamAssetIds(Long assetId);
}
