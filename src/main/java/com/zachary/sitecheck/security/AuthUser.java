package com.zachary.sitecheck.security;

import com.zachary.sitecheck.entity.Role;

/**
 * The logged-in user as read from the JWT. Used as the Spring Security principal,
 * so controllers can take {@code @AuthenticationPrincipal AuthUser} without a DB lookup.
 */
public record AuthUser(Long id, String email, Role role) {
}
