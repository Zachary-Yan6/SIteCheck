package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.FailedItemDto;
import com.zachary.sitecheck.service.ManagerService;
import com.zachary.sitecheck.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Manager pages (FR-30, FR-31). Managers see every site, so there is no per-site access check here. */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerController {

    private final ManagerService managerService;
    private final ReportService reportService;

    public ManagerController(ManagerService managerService, ReportService reportService) {
        this.managerService = managerService;
        this.reportService = reportService;
    }

    /**
     * Failed items, newest first. Defaults to the last 30 days, all sites.
     * Example: {@code GET /api/manager/failed-items?siteId=3&from=2026-10-01&to=2026-10-13}
     */
    @GetMapping("/failed-items")
    public List<FailedItemDto> failedItems(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ManagerService.SITE_ZONE);
        LocalDate start = from != null ? from : end.minusDays(30);
        return managerService.failedItems(siteId, start, end);
    }

    /**
     * PDF report of one site's inspections (FR-31), downloaded as a file. Defaults to the last 30 days.
     * Example: {@code GET /api/manager/sites/3/report?from=2026-10-01&to=2026-10-13}
     */
    @GetMapping("/sites/{siteId}/report")
    public ResponseEntity<byte[]> siteReport(
            @PathVariable Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ManagerService.SITE_ZONE);
        LocalDate start = from != null ? from : end.minusDays(30);
        ReportService.ReportFile report = reportService.siteReport(siteId, start, end);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.bytes());
    }
}
