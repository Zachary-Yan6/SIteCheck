package com.zachary.sitecheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Push notification settings ({@code sitecheck.push.*}).
 *
 * @param expoUrl     Expo's send endpoint
 * @param accessToken Expo access token; only needed if enhanced push security is enabled
 */
@ConfigurationProperties("sitecheck.push")
public record PushProperties(String expoUrl, String accessToken) {
}
