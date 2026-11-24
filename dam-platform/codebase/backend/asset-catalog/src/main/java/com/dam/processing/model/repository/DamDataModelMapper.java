package com.dam.processing.model.repository;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.processing.model.entity.DamDataModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamDataModelMapper extends BaseMapper<DamDataModel> {
}
