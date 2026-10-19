package com.zachary.sitecheck.mapper;

import java.time.OffsetDateTime;

/** Most recent submitted inspection of one template at one site. */
public record LastInspection(Long siteId, Long templateId, OffsetDateTime lastSubmittedAt) {
}
