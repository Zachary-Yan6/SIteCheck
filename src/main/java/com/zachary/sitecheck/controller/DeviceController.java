package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.RegisterDeviceRequest;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The app registers its push token after login (FR-27) and removes it on logout. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** Safe to call on every app start: registering the same token again just refreshes it. */
    @PostMapping
    public ResponseEntity<Void> register(@AuthenticationPrincipal AuthUser user,
                                         @Valid @RequestBody RegisterDeviceRequest request) {
        deviceService.register(user, request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal AuthUser user, @PathVariable String token) {
        deviceService.unregister(user, token);
        return ResponseEntity.noContent().build(); // also when it was already gone: the goal is reached
    }
}
