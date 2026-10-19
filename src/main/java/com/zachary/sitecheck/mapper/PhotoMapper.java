package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.Photo;
import org.apache.ibatis.annotations.Mapper;

/** Access to {@link Photo}. */
@Mapper
public interface PhotoMapper extends BaseMapper<Photo> {
}
