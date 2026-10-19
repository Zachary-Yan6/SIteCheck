package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.Site;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** CRUD for {@link Site}. */
@Mapper
public interface SiteMapper extends BaseMapper<Site> {

    /** Sites the given inspector is assigned to (FR-02), alphabetical. */
    @Select("""
            SELECT s.*
            FROM sites s
            JOIN site_inspectors si ON si.site_id = s.id
            WHERE si.user_id = #{userId}
            ORDER BY s.name
            """)
    List<Site> selectAssignedTo(Long userId);
}
