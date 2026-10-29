package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.MetadataReportSqlParser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表SQL解析结果表 Mapper
 */
@Mapper
public interface MetadataReportSqlParserMapper extends BaseMapper<MetadataReportSqlParser> {
}
