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

@Component
public class AccessKeyFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE = "MUSIC_AI_SESSION";

    private final AccessKeyAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public AccessKeyFilter(AccessKeyAuthenticator authenticator, ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authenticator.enabled()
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/session")
                || request.getMethod().equals("OPTIONS");
    }

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
