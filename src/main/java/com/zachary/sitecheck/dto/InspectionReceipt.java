package com.zachary.sitecheck.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What the server has stored for an inspection. The same receipt comes back for the
 * first upload and for every retry, so the app handles both identically.
 *
 * @param pendingPhotoIds photos the server is still waiting for; after an app restart the
 *                        sync queue uploads only these (acceptance scenario 3)
 */
public record InspectionReceipt(UUID id, OffsetDateTime receivedAt, List<UUID> pendingPhotoIds) {
}
