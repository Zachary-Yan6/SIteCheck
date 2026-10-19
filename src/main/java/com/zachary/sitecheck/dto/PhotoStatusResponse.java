package com.zachary.sitecheck.dto;

import com.zachary.sitecheck.entity.PhotoStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PhotoStatusResponse(UUID id, PhotoStatus status, OffsetDateTime uploadedAt) {
}
