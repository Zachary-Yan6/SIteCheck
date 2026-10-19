package com.zachary.sitecheck.mapper;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One FAIL answer with its context, as read by {@link ManagerMapper#selectFailedItems}. */
public record FailedItemRow(
        UUID inspectionId,
        Long siteId,
        String siteName,
        String templateName,
        Long templateItemId,
        Integer position,
        String itemTitle,
        String note,
        OffsetDateTime submittedAt,
        String inspectorName) {
}
