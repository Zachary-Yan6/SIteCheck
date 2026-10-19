package com.zachary.sitecheck.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Past inspections of a site (FR-09). */
@Mapper
public interface SiteHistoryMapper {

    /** Newest first, with how many items were answered and how many failed. */
    @Select("""
            SELECT i.id, t.name AS template_name, u.full_name AS inspector_name, i.submitted_at,
                   count(r.template_item_id) AS item_count,
                   count(r.template_item_id) FILTER (WHERE r.result = 'FAIL') AS fail_count
            FROM inspections i
            JOIN inspection_templates t      ON t.id = i.template_id
            JOIN users u                     ON u.id = i.inspector_id
            LEFT JOIN inspection_results r   ON r.inspection_id = i.id
            WHERE i.site_id = #{siteId}
            GROUP BY i.id, t.name, u.full_name, i.submitted_at
            ORDER BY i.submitted_at DESC
            LIMIT #{limit}
            """)
    List<HistoryRow> selectHistory(@Param("siteId") Long siteId, @Param("limit") int limit);

    /** Titles of the failed items of the given inspections, in checklist order. */
    @Select("""
            <script>
            SELECT r.inspection_id, ti.title
            FROM inspection_results r
            JOIN template_items ti ON ti.id = r.template_item_id
            WHERE r.result = 'FAIL' AND r.inspection_id IN
              <foreach collection="inspectionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY r.inspection_id, ti.position
            </script>
            """)
    List<FailedTitleRow> selectFailedTitles(@Param("inspectionIds") List<UUID> inspectionIds);

    record HistoryRow(UUID id, String templateName, String inspectorName, OffsetDateTime submittedAt,
                      Long itemCount, Long failCount) {
    }

    record FailedTitleRow(UUID inspectionId, String title) {
    }
}
