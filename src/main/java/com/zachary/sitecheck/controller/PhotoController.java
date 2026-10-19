package com.zachary.sitecheck.controller;

import com.zachary.sitecheck.dto.PhotoStatusResponse;
import com.zachary.sitecheck.dto.UploadUrlRequest;
import com.zachary.sitecheck.dto.UploadUrlResponse;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.service.PhotoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Photo upload endpoints used by the app's sync queue, one photo at a time (FR-22). */
@RestController
@RequestMapping("/api/photos/{photoId}")
@PreAuthorize("hasRole('INSPECTOR')")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /** Step 1: get a presigned S3 URL to PUT the JPEG to. */
    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(@AuthenticationPrincipal AuthUser user, @PathVariable UUID photoId,
                                       @Valid @RequestBody UploadUrlRequest request) {
        return photoService.createUploadUrl(user, photoId, request.sizeBytes());
    }

    /** Step 3: tell the server the PUT finished. 409 means S3 doesn't have it - upload again. */
    @PostMapping("/confirm")
    public PhotoStatusResponse confirm(@AuthenticationPrincipal AuthUser user, @PathVariable UUID photoId) {
        return photoService.confirmUpload(user, photoId);
    }
}
