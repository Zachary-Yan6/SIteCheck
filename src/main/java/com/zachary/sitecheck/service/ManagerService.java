package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.client.PhotoLinks;
import com.zachary.sitecheck.dto.FailedItemDto;
import com.zachary.sitecheck.dto.FailedItemDto.PhotoLinkDto;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.PhotoStatus;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.FailedItemRow;
import com.zachary.sitecheck.mapper.ManagerMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerService {

    /** Dates in filters are Irish calendar days, whatever time zone the server runs in. */
    public static final ZoneId SITE_ZONE = ZoneId.of("Europe/Dublin");
    /** The list is for reading on a phone; a narrower filter finds older items. */
    static final int MAX_ITEMS = 200;
    static final int MAX_RANGE_DAYS = 366;

    private final ManagerMapper managerMapper;
    private final PhotoMapper photoMapper;
    private final PhotoLinks photoLinks;

    public ManagerService(ManagerMapper managerMapper, PhotoMapper photoMapper, PhotoLinks photoLinks) {
        this.managerMapper = managerMapper;
        this.photoMapper = photoMapper;
        this.photoLinks = photoLinks;
    }

    /**
     * Failed items across all sites (FR-30), optionally for one site, submitted between two dates
     * (both inclusive). Only photos that actually reached S3 are linked.
     */
    @Transactional(readOnly = true)
    public List<FailedItemDto> failedItems(Long siteId, LocalDate from, LocalDate to) {
        checkRange(from, to);

        List<FailedItemRow> rows = managerMapper.selectFailedItems(
                siteId,
                from.atStartOfDay(SITE_ZONE).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(SITE_ZONE).toOffsetDateTime(), // exclusive end
                MAX_ITEMS);
        if (rows.isEmpty()) {
            return List.of();
        }

        // (inspection, item) -> its uploaded photos, in one query for all rows
        List<UUID> inspectionIds = rows.stream().map(FailedItemRow::inspectionId).distinct().toList();
        Map<PhotoKey, List<Photo>> photosByItem = photoMapper.selectList(Wrappers.<Photo>lambdaQuery()
                        .in(Photo::getInspectionId, inspectionIds)
                        .eq(Photo::getStatus, PhotoStatus.UPLOADED))
                .stream()
                .sorted(Comparator.comparing(Photo::getTakenAt))
                .collect(Collectors.groupingBy(p -> new PhotoKey(p.getInspectionId(), p.getTemplateItemId())));

        return rows.stream()
                .map(row -> new FailedItemDto(
                        row.inspectionId(), row.siteId(), row.siteName(), row.templateName(),
                        row.position(), row.itemTitle(), row.note(), row.submittedAt(), row.inspectorName(),
                        photosByItem.getOrDefault(new PhotoKey(row.inspectionId(), row.templateItemId()), List.of())
                                .stream()
                                .map(this::toLink)
                                .toList()))
                .toList();
    }

    /** Shared by the list and the PDF report: a sane, forward date range. */
    static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must not be after 'to'");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest("The date range can be at most " + MAX_RANGE_DAYS + " days");
        }
    }

    private PhotoLinkDto toLink(Photo photo) {
        return new PhotoLinkDto(photo.getId(), photoLinks.downloadUrl(photo.getS3Key()),
                photo.getTakenAt(), photo.getLatitude(), photo.getLongitude());
    }

    private record PhotoKey(UUID inspectionId, Long templateItemId) {
    }
}
