package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.InspectionReceipt;
import com.zachary.sitecheck.dto.SubmitInspectionRequest;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.InspectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * Upload a signed inspection from the sync queue. Safe to retry: 201 the first time,
     * 200 with the same receipt for every retry.
     */
    @PostMapping
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<InspectionReceipt> submit(@AuthenticationPrincipal AuthUser user,
                                                    @Valid @RequestBody SubmitInspectionRequest request) {
        InspectionService.SubmitOutcome outcome = inspectionService.submit(user, request);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.receipt());
    }
}
