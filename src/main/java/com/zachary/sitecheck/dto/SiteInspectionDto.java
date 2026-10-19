package com.zachary.sitecheck.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One past inspection in a site's history (FR-09). */
public record SiteInspectionDto(
        UUID id,
        String templateName,
        String inspectorName,
        OffsetDateTime submittedAt,
        int itemCount,
        int failCount,
        /** Titles of the failed items, in checklist order; empty if everything passed. */
        List<String> failedItems) {
}
