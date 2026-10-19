package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.SiteTemplate;
import org.apache.ibatis.annotations.Mapper;

/** Access to {@code site_templates} (composite key, so use wrappers rather than *ById methods). */
@Mapper
public interface SiteTemplateMapper extends BaseMapper<SiteTemplate> {
}
