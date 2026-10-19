package com.zachary.sitecheck.dto;

import com.zachary.sitecheck.entity.Site;
import java.math.BigDecimal;
import java.util.List;

/**
 * A site plus the checks it needs. Schedules refer to templates by id; the templates
 * themselves are sent once in the response.
 */
public record SiteDto(Long id, String name, String address, BigDecimal latitude, BigDecimal longitude,
                      List<ScheduleDto> schedules) {

    public static SiteDto from(Site site, List<ScheduleDto> schedules) {
        return new SiteDto(site.getId(), site.getName(), site.getAddress(),
                site.getLatitude(), site.getLongitude(), schedules);
    }
}
