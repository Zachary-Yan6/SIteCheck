package com.zachary.sitecheck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of POST /api/devices: this phone's Expo push token (FR-27). */
public record RegisterDeviceRequest(
        @NotBlank @Pattern(regexp = "^(ExponentPushToken|ExpoPushToken)\\[[^\\]]+]$",
                message = "must be an Expo push token") String token,
        @NotBlank @Pattern(regexp = "android|ios") String platform) {
}
