package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.SiteInspector;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * Ends the assignment and keeps it in {@code site_inspector_history}, in one statement.
     * Returns 1 if there was an assignment, 0 if the inspector wasn't on the site.
     */
    @Insert("""
            WITH removed AS (
                DELETE FROM site_inspectors WHERE site_id = #{siteId} AND user_id = #{userId}
                RETURNING site_id, user_id, assigned_at
            )
            INSERT INTO site_inspector_history (site_id, user_id, assigned_at)
            SELECT site_id, user_id, assigned_at FROM removed
            """)
    int unassign(@Param("siteId") Long siteId, @Param("userId") Long userId);

    /** True if an assignment that has since ended was running at that moment. */
    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM site_inspector_history
                WHERE site_id = #{siteId} AND user_id = #{userId}
                  AND assigned_at <= #{at} AND #{at} < unassigned_at
            )
            """)
    boolean wasAssignedAt(@Param("siteId") Long siteId, @Param("userId") Long userId,
                          @Param("at") OffsetDateTime at);
}
