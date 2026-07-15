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

/**
 * 校验访问密钥并签发、验证有时效的会话令牌。
 */
@Component
public class AccessKeyAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] accessKey;
    private final long sessionTtlSeconds;
    private final Clock clock;

    /**
     * 使用配置的访问密钥和会话有效期创建认证器。
     *
     * @param accessKey 服务访问密钥；为空时关闭访问保护
     * @param sessionTtlSeconds 会话令牌有效秒数
     */
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

    /**
     * 判断访问密钥保护是否启用。
     *
     * @return 配置了非空访问密钥时为 {@code true}
     */
    public boolean enabled() {
        return accessKey.length > 0;
    }

    /**
     * 以恒定时间比较候选值和配置的访问密钥，降低时序侧信道泄露风险。
     *
     * @param candidate 候选访问密钥
     * @return 保护已启用且密钥匹配时为 {@code true}
     */
    public boolean matchesAccessKey(String candidate) {
        return enabled() && candidate != null && constantTimeEquals(accessKey,
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发包含到期时间并由访问密钥认证的会话令牌，避免后续请求反复传输原始密钥。
     *
     * @return URL 安全的会话令牌
     * @throws IllegalStateException 访问保护未启用时抛出
     */
    public String issueSessionToken() {
        if (!enabled()) {
            throw new IllegalStateException("Access key protection is disabled");
        }
        // 令牌仅携带到期时间和 HMAC，可无服务端会话状态地校验且不会暴露原始访问密钥。
        String expiresAt = Long.toString(Instant.now(clock).getEpochSecond() + sessionTtlSeconds);
        return expiresAt + "." + sign(expiresAt);
    }

    /**
     * 校验会话令牌的有效期与签名，防止客户端篡改到期时间。
     *
     * @param token 待校验的会话令牌
     * @return 令牌格式正确、未过期且签名匹配时为 {@code true}
     */
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

    /**
     * 返回会话令牌有效期。
     *
     * @return 有效秒数
     */
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
