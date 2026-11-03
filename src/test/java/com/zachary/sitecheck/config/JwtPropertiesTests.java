package com.zachary.sitecheck.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtPropertiesTests {

    private static final Duration TTL = Duration.ofHours(24);

    @Test
    void missingSecretStopsStartup() {
        assertThatThrownBy(() -> new JwtProperties("", TTL, false)).hasMessageContaining("not set");
        assertThatThrownBy(() -> new JwtProperties(null, TTL, false)).hasMessageContaining("not set");
    }

    @Test
    void shortSecretStopsStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", TTL, false)).hasMessageContaining("at least 32");
    }

    @Test
    void devSecretOnlyWorksInTheDevProfile() {
        assertThatThrownBy(() -> new JwtProperties(JwtProperties.DEV_SECRET, TTL, false))
                .hasMessageContaining("public development secret");
        assertThatCode(() -> new JwtProperties(JwtProperties.DEV_SECRET, TTL, true)).doesNotThrowAnyException();
    }

    @Test
    void realSecretIsAccepted() {
        assertThatCode(() -> new JwtProperties("a-real-random-secret-of-more-than-32-bytes", TTL, false))
                .doesNotThrowAnyException();
    }
}
