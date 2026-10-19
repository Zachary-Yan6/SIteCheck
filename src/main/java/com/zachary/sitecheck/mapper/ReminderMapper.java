package com.zachary.sitecheck.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReminderMapper {

    /**
     * Every (assigned inspector, site, check) with when the check was last done by anyone.
     * The data is small - a few sites times a few checks per inspector - so it's all read at once.
     */
    @Select("""
            SELECT si.user_id, s.id AS site_id, s.name AS site_name, t.name AS template_name, t.frequency_days,
                   (SELECT max(i.submitted_at) FROM inspections i
                    WHERE i.site_id = s.id AND i.template_id = t.id) AS last_inspected_at
            FROM site_inspectors si
            JOIN sites s                ON s.id = si.site_id
            JOIN site_templates st      ON st.site_id = s.id
            JOIN inspection_templates t ON t.id = st.template_id
            ORDER BY si.user_id, s.id
            """)
    List<ScheduleRow> selectSchedules();

    /** Returns 1 if this reminder is ours to send, 0 if it already went out today. */
    @Insert("""
            INSERT INTO reminder_log (user_id, site_id, sent_on) VALUES (#{userId}, #{siteId}, #{day})
            ON CONFLICT DO NOTHING
            """)
    int claim(@Param("userId") Long userId, @Param("siteId") Long siteId, @Param("day") LocalDate day);

    /** Undo a claim when sending failed, so the next run can try again. */
    @Delete("DELETE FROM reminder_log WHERE user_id = #{userId} AND site_id = #{siteId} AND sent_on = #{day}")
    void release(@Param("userId") Long userId, @Param("siteId") Long siteId, @Param("day") LocalDate day);

    record ScheduleRow(Long userId, Long siteId, String siteName, String templateName, Integer frequencyDays,
                       OffsetDateTime lastInspectedAt) {
    }
}
