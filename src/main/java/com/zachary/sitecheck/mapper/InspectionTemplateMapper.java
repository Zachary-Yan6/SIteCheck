package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.InspectionTemplate;
import org.apache.ibatis.annotations.Mapper;

/** CRUD for {@link InspectionTemplate}. */
@Mapper
public interface InspectionTemplateMapper extends BaseMapper<InspectionTemplate> {
}
