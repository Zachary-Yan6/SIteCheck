package com.zachary.sitecheck.dto;

import java.time.Instant;

/** {@code expiresAt} lets the app know when to ask for a new login once it is online (FR-03). */
public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
}
