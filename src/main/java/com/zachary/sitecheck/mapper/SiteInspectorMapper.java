package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.SiteInspector;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Access to {@code site_inspectors} (composite key, so use wrappers rather than *ById methods). */
@Mapper
public interface SiteInspectorMapper extends BaseMapper<SiteInspector> {

    /** Assigns unless already assigned; returns 1 if a row was added, 0 if it already existed. */
    @Insert("""
            INSERT INTO site_inspectors (site_id, user_id)
            VALUES (#{siteId}, #{userId})
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("siteId") Long siteId, @Param("userId") Long userId);
}
