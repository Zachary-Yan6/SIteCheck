package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.BootstrapResponse;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.SyncService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /** FR-07: called right after login and on pull-to-refresh to fill the app's SQLite cache. */
    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@AuthenticationPrincipal AuthUser user) {
        return syncService.bootstrap(user);
    }
}
