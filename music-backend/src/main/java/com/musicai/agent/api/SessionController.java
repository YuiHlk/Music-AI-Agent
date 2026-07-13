package com.musicai.agent.api;

import com.musicai.agent.infrastructure.security.AccessKeyAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final AccessKeyAuthenticator authenticator;
    private final boolean secureCookie;

    public SessionController(AccessKeyAuthenticator authenticator,
                             @Value("${music-ai.security.secure-cookie:false}") boolean secureCookie) {
        this.authenticator = authenticator;
        this.secureCookie = secureCookie;
    }

    @GetMapping
    SessionStatus status(HttpServletRequest request) {
        boolean enabled = authenticator.enabled();
        boolean authenticated = !enabled || authenticator.isValidSessionToken(
                AccessKeyFilter.cookieValue(request, AccessKeyFilter.SESSION_COOKIE));
        return new SessionStatus(enabled, authenticated);
    }

    @PostMapping
    SessionStatus login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (!authenticator.enabled()) {
            return new SessionStatus(false, true);
        }
        if (!authenticator.matchesAccessKey(request.accessKey())) {
            throw new InvalidAccessKeyException();
        }
        ResponseCookie cookie = ResponseCookie.from(AccessKeyFilter.SESSION_COOKIE,
                        authenticator.issueSessionToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api")
                .maxAge(Duration.ofSeconds(authenticator.sessionTtlSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new SessionStatus(true, true);
    }

    public record LoginRequest(@NotBlank @Size(max = 512) String accessKey) {
    }

    public record SessionStatus(boolean securityEnabled, boolean authenticated) {
    }
}
