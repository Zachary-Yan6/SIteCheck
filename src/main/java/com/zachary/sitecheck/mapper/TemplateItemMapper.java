package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.TemplateItem;
import org.apache.ibatis.annotations.Mapper;

/** CRUD for {@link TemplateItem}. */
@Mapper
public interface TemplateItemMapper extends BaseMapper<TemplateItem> {
}
