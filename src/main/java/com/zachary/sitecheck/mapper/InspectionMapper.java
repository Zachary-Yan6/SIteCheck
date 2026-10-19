package com.zachary.sitecheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zachary.sitecheck.entity.Inspection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Access to {@link Inspection}. */
@Mapper
public interface InspectionMapper extends BaseMapper<Inspection> {

    /**
     * Inserts the inspection unless one with the same id already exists.
     * Returns 1 if inserted, 0 if the id was already there.
     *
     * <p>If two retries of the same upload arrive at the same moment, Postgres makes the second
     * INSERT wait for the first transaction: it then returns 0 if the first committed, or inserts
     * normally if the first rolled back. So exactly one copy is ever stored.
     */
    @Insert("""
            INSERT INTO inspections (id, site_id, template_id, inspector_id, started_at, submitted_at, signature_png)
            VALUES (#{id}, #{siteId}, #{templateId}, #{inspectorId}, #{startedAt}, #{submittedAt}, #{signaturePng})
            ON CONFLICT (id) DO NOTHING
            """)
    int insertIfAbsent(Inspection inspection);

    /**
     * Latest submitted_at per (site, template) for the given sites, by any inspector - a check done
     * by a colleague counts too. Pairs never inspected are simply absent. Served by
     * idx_inspections_site_template. Columns are in the same order as {@link LastInspection}'s fields.
     */
    @Select("""
            <script>
            SELECT site_id, template_id, max(submitted_at) AS last_submitted_at
            FROM inspections
            WHERE site_id IN
              <foreach collection="siteIds" item="siteId" open="(" separator="," close=")">#{siteId}</foreach>
            GROUP BY site_id, template_id
            </script>
            """)
    List<LastInspection> selectLastInspections(@Param("siteIds") List<Long> siteIds);
}
