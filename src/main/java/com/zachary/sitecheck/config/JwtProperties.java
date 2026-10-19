package com.zachary.sitecheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code sitecheck.jwt.*}).
 *
 * @param secret HMAC-SHA256 signing key, at least 32 bytes
 * @param ttl    how long a token is valid. When it expires while the phone is offline the
 *               app keeps working and asks for a new login once it is back online (FR-03).
 */
@ConfigurationProperties("sitecheck.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
