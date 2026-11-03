package com.zachary.sitecheck.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code sitecheck.jwt.*}). Checked at startup: a missing, short or publicly known
 * secret would let anyone forge tokens, so the app refuses to start instead of running with it.
 *
 * @param secret         HMAC-SHA256 signing key, at least 32 bytes
 * @param ttl            how long a token is valid. When it expires while the phone is offline the
 *                       app keeps working and asks for a new login once it is back online (FR-03).
 * @param allowDevSecret true only in the dev profile, which uses the secret committed to this repo
 */
@ConfigurationProperties("sitecheck.jwt")
public record JwtProperties(String secret, Duration ttl, boolean allowDevSecret) {

    /** The secret in application-dev.yml - public, so only ever accepted together with allowDevSecret. */
    static final String DEV_SECRET = "local-dev-secret-change-me-0123456789abcdef";
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set - use a random value of at least "
                    + MIN_SECRET_BYTES + " bytes");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (secret.equals(DEV_SECRET) && !allowDevSecret) {
            throw new IllegalStateException("JWT_SECRET is the public development secret - set a real one");
        }
    }
}
