package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.InspectionResult;
import org.apache.ibatis.annotations.Mapper;

/** Access to {@code inspection_results} (composite key, so use wrappers rather than *ById methods). */
@Mapper
public interface InspectionResultMapper extends BaseMapper<InspectionResult> {
}
