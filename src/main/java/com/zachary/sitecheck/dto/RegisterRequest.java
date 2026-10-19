package com.zachary.sitecheck.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self sign-up. Always creates an INSPECTOR - managers only come from seed data.
 * Password max 72: BCrypt ignores everything after 72 bytes.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName) {
}
