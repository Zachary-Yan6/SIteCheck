package com.zachary.sitecheck.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only reporting queries for the manager pages. Not tied to one table, so no BaseMapper. */
@Mapper
public interface ManagerMapper {

    /**
     * FAIL answers submitted in [from, to), newest first, optionally for one site (FR-30).
     * Columns are in the same order as {@link FailedItemRow}'s fields.
     */
    @Select("""
            <script>
            SELECT i.id AS inspection_id, s.id AS site_id, s.name AS site_name, t.name AS template_name,
                   ti.id AS template_item_id, ti.position, ti.title AS item_title, r.note,
                   i.submitted_at, u.full_name AS inspector_name
            FROM inspection_results r
            JOIN inspections i          ON i.id = r.inspection_id
            JOIN sites s                ON s.id = i.site_id
            JOIN inspection_templates t ON t.id = i.template_id
            JOIN template_items ti      ON ti.id = r.template_item_id
            JOIN users u                ON u.id = i.inspector_id
            WHERE r.result = 'FAIL'
              AND i.submitted_at &gt;= #{from} AND i.submitted_at &lt; #{to}
              <if test="siteId != null">AND i.site_id = #{siteId}</if>
            ORDER BY i.submitted_at DESC, ti.position
            LIMIT #{limit}
            </script>
            """)
    List<FailedItemRow> selectFailedItems(@Param("siteId") Long siteId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to,
                                          @Param("limit") int limit);

    /** Inspections of one site submitted in [from, to), oldest first, for the PDF report (FR-31). */
    @Select("""
            SELECT i.id, t.name AS template_name, u.full_name AS inspector_name, i.submitted_at, i.signature_png
            FROM inspections i
            JOIN inspection_templates t ON t.id = i.template_id
            JOIN users u                ON u.id = i.inspector_id
            WHERE i.site_id = #{siteId} AND i.submitted_at >= #{from} AND i.submitted_at < #{to}
            ORDER BY i.submitted_at
            LIMIT #{limit}
            """)
    List<ReportInspectionRow> selectReportInspections(@Param("siteId") Long siteId,
                                                      @Param("from") OffsetDateTime from,
                                                      @Param("to") OffsetDateTime to,
                                                      @Param("limit") int limit);

    /** Every answer of the given inspections, in checklist order. */
    @Select("""
            <script>
            SELECT r.inspection_id, r.template_item_id, ti.position, ti.title, r.result, r.note
            FROM inspection_results r
            JOIN template_items ti ON ti.id = r.template_item_id
            WHERE r.inspection_id IN
              <foreach collection="inspectionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY r.inspection_id, ti.position
            </script>
            """)
    List<ReportResultRow> selectReportResults(@Param("inspectionIds") List<UUID> inspectionIds);
}
