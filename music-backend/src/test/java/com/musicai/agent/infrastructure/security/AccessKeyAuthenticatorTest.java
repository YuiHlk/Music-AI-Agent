package com.musicai.agent.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AccessKeyAuthenticatorTest {

    @Test
    void issuesSignedExpiringTokensWithoutStoringTheAccessKeyInTheCookie() {
        Clock now = Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC);
        AccessKeyAuthenticator authenticator = new AccessKeyAuthenticator("portfolio-secret", 3600, now);

        String token = authenticator.issueSessionToken();

        assertThat(authenticator.matchesAccessKey("portfolio-secret")).isTrue();
        assertThat(token).doesNotContain("portfolio-secret");
        assertThat(authenticator.isValidSessionToken(token)).isTrue();
        assertThat(authenticator.isValidSessionToken(token + "tampered")).isFalse();

        Clock expired = Clock.fixed(Instant.parse("2026-07-13T09:00:01Z"), ZoneOffset.UTC);
        assertThat(new AccessKeyAuthenticator("portfolio-secret", 3600, expired).isValidSessionToken(token)).isFalse();
    }
}
