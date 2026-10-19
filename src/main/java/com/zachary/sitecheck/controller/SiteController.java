package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.SiteInspectionDto;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.SiteHistoryService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sites/{siteId}")
public class SiteController {

    private final SiteHistoryService historyService;

    public SiteController(SiteHistoryService historyService) {
        this.historyService = historyService;
    }

    /** FR-09: e.g. {@code GET /api/sites/3/inspections?limit=30}, newest first. */
    @GetMapping("/inspections")
    public List<SiteInspectionDto> history(@AuthenticationPrincipal AuthUser user,
                                           @PathVariable Long siteId,
                                           @RequestParam(defaultValue = "" + SiteHistoryService.DEFAULT_LIMIT) int limit) {
        return historyService.history(user, siteId, limit);
    }
}
