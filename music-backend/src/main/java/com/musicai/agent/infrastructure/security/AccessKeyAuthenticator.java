package com.musicai.agent.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Component
public class AccessKeyAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] accessKey;
    private final long sessionTtlSeconds;
    private final Clock clock;

    @Autowired
    public AccessKeyAuthenticator(@Value("${music-ai.security.access-key:}") String accessKey,
                                  @Value("${music-ai.security.session-ttl-seconds:28800}") long sessionTtlSeconds) {
        this(accessKey, sessionTtlSeconds, Clock.systemUTC());
    }

    AccessKeyAuthenticator(String accessKey, long sessionTtlSeconds, Clock clock) {
        this.accessKey = accessKey == null ? new byte[0] : accessKey.getBytes(StandardCharsets.UTF_8);
        if (sessionTtlSeconds < 60 || sessionTtlSeconds > 604800) {
            throw new IllegalArgumentException("Session TTL must be between 60 and 604800 seconds");
        }
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.clock = clock;
    }

    public boolean enabled() {
        return accessKey.length > 0;
    }

    public boolean matchesAccessKey(String candidate) {
        return enabled() && candidate != null && constantTimeEquals(accessKey,
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    public String issueSessionToken() {
        if (!enabled()) {
            throw new IllegalStateException("Access key protection is disabled");
        }
        String expiresAt = Long.toString(Instant.now(clock).getEpochSecond() + sessionTtlSeconds);
        return expiresAt + "." + sign(expiresAt);
    }

    public boolean isValidSessionToken(String token) {
        if (!enabled() || token == null) {
            return false;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return false;
        }
        String expiresAt = token.substring(0, separator);
        try {
            if (Long.parseLong(expiresAt) < Instant.now(clock).getEpochSecond()) {
                return false;
            }
        } catch (NumberFormatException exception) {
            return false;
        }
        return constantTimeEquals(sign(expiresAt).getBytes(StandardCharsets.US_ASCII),
                token.substring(separator + 1).getBytes(StandardCharsets.US_ASCII));
    }

    public long sessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(accessKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign access session", exception);
        }
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        return java.security.MessageDigest.isEqual(left, right);
    }
}
