package com.zachary.sitecheck.dto;

import com.zachary.sitecheck.entity.ItemResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A signed inspection uploaded from the phone's sync queue (FR-18).
 * Photo files are not in here - only their metadata; the files follow via S3 (FR-21/22).
 *
 * @param id           UUID generated on the phone when the draft was created; the idempotency key (FR-20)
 * @param signaturePng the signature image; sent as a base64 string in JSON, which Jackson decodes to bytes
 */
public record SubmitInspectionRequest(
        @NotNull UUID id,
        @NotNull Long siteId,
        @NotNull Long templateId,
        @NotNull OffsetDateTime startedAt,
        @NotNull OffsetDateTime submittedAt,
        @NotNull @Size(min = 1, max = 200_000) byte[] signaturePng,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull ResultRequest> results,
        @Size(max = 100) List<@Valid @NotNull PhotoRequest> photos) {

    public SubmitInspectionRequest {
        photos = photos == null ? List.of() : photos;
    }

    public record ResultRequest(
            @NotNull Long templateItemId,
            @NotNull ItemResult result,
            @Size(max = 2000) String note) {
    }

    public record PhotoRequest(
            @NotNull UUID id,
            @NotNull Long templateItemId,
            @NotNull OffsetDateTime takenAt,
            BigDecimal latitude,
            BigDecimal longitude) {
    }
}
