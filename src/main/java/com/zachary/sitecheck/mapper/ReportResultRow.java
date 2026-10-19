package com.zachary.sitecheck.mapper;

import java.util.UUID;

/** One answer in the PDF report, as read by {@link ManagerMapper#selectReportResults}. */
public record ReportResultRow(
        UUID inspectionId,
        Long templateItemId,
        Integer position,
        String title,
        String result,
        String note) {
}
