package com.musicai.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicai.agent.infrastructure.security.AccessKeyAuthenticator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * 使用访问密钥 Header 或 HttpOnly 会话 cookie 保护应用 REST API。
 * cookie 方案使无法附加自定义 Header 的浏览器 EventSource 也能认证。
 */
@Component
public class AccessKeyFilter extends OncePerRequestFilter {

    /** 浏览器访问会话的 cookie 名称。 */
    public static final String SESSION_COOKIE = "MUSIC_AI_SESSION";

    private final AccessKeyAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    /**
     * @param authenticator 密钥和会话验证器
     * @param objectMapper 401 响应序列化器
     */
    public AccessKeyFilter(AccessKeyAuthenticator authenticator, ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authenticator.enabled()
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/session")
                || request.getMethod().equals("OPTIONS");
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String accessKey = request.getHeader("X-Music-AI-Key");
        String sessionToken = cookieValue(request, SESSION_COOKIE);
        if (authenticator.matchesAccessKey(accessKey) || authenticator.isValidSessionToken(sessionToken)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 401,
                "code", "AUTHENTICATION_REQUIRED",
                "message", "A valid project access session is required",
                "path", request.getRequestURI(),
                "timestamp", Instant.now().toString()));
    }

    /**
     * @param request HTTP 请求
     * @param name cookie 名称
     * @return 第一个同名 cookie 值，不存在时为 {@code null}
     */
    static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }
}
