package com.zachary.sitecheck.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything the app caches in SQLite at login / pull-to-refresh (FR-07).
 * The app replaces its cached sites and templates with this snapshot.
 *
 * @param serverTime lets the app compute due dates against server time, not a possibly wrong phone clock
 */
public record BootstrapResponse(Instant serverTime, List<SiteDto> sites, List<TemplateDto> templates) {
}
