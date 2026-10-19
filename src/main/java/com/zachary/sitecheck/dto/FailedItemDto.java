package com.zachary.sitecheck.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A failed checklist item for the manager list (FR-30), with its evidence photos. */
public record FailedItemDto(
        UUID inspectionId,
        Long siteId,
        String siteName,
        String templateName,
        Integer position,
        String itemTitle,
        String note,
        OffsetDateTime submittedAt,
        String inspectorName,
        List<PhotoLinkDto> photos) {

    /**
     * @param url presigned GET link, valid for a few minutes only (NFR-05)
     */
    public record PhotoLinkDto(UUID id, String url, OffsetDateTime takenAt, BigDecimal latitude, BigDecimal longitude) {
    }
}
