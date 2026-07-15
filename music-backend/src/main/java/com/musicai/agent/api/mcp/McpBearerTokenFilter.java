package com.musicai.agent.api.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/**
 * 在 MCP servlet 之前验证严格的 Bearer token，并直接返回稳定的 HTTP 401 JSON。
 */
final class McpBearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    /**
     * @param expectedToken 配置的共享 token
     * @param objectMapper 未认证响应序列化器
     */
    McpBearerTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (matches(authorization)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 401,
                "code", "MCP_AUTHENTICATION_REQUIRED",
                "message", "A valid MCP bearer token is required",
                "path", request.getRequestURI(),
                "timestamp", Instant.now().toString()));
    }

    private boolean matches(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] candidate = authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        // 避免普通字符串比较的提前退出泄露可利用的逐字节时序差异。
        return MessageDigest.isEqual(expectedToken, candidate);
    }
}
