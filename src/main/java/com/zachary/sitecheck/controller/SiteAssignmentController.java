package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.UserResponse;
import com.zachary.sitecheck.service.SiteAssignmentService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Managers assigning inspectors to sites. PUT / DELETE on the pair, so retries are always safe. */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasRole('MANAGER')")
public class SiteAssignmentController {

    private final SiteAssignmentService assignmentService;

    public SiteAssignmentController(SiteAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/inspectors")
    public List<UserResponse> inspectors() {
        return assignmentService.inspectors();
    }

    @GetMapping("/sites/{siteId}/inspectors")
    public List<UserResponse> assigned(@PathVariable Long siteId) {
        return assignmentService.assignedTo(siteId);
    }

    @PutMapping("/sites/{siteId}/inspectors/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable Long siteId, @PathVariable Long userId) {
        assignmentService.assign(siteId, userId);
    }

    @DeleteMapping("/sites/{siteId}/inspectors/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable Long siteId, @PathVariable Long userId) {
        assignmentService.unassign(siteId, userId);
    }
}
