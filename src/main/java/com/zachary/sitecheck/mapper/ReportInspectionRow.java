package com.zachary.sitecheck.mapper;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One inspection in the PDF report, as read by {@link ManagerMapper#selectReportInspections}. */
public record ReportInspectionRow(
        UUID id,
        String templateName,
        String inspectorName,
        OffsetDateTime submittedAt,
        byte[] signaturePng) {
}
