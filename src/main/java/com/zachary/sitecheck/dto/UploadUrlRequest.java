package com.zachary.sitecheck.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param sizeBytes exact size of the compressed JPEG the app is about to upload. It is signed into
 *                  the URL, so S3 rejects an upload of any other size.
 */
public record UploadUrlRequest(@NotNull @Positive Long sizeBytes) {
}
