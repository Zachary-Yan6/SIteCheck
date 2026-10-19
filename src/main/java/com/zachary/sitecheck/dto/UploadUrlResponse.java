package com.zachary.sitecheck.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Where and how the app uploads one photo.
 *
 * @param alreadyUploaded true if the server already has this photo (e.g. the confirm response was
 *                        lost) - the app should skip the upload and mark it done; url is then null
 * @param method          always PUT
 * @param headers         headers the app must send exactly, because they are part of the signature
 */
public record UploadUrlResponse(boolean alreadyUploaded, String url, String method,
                                Map<String, String> headers, Instant expiresAt) {

    public static UploadUrlResponse uploaded() {
        return new UploadUrlResponse(true, null, null, Map.of(), null);
    }
}
