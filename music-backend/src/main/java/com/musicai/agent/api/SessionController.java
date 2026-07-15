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

/** 管理访问密钥到 HttpOnly cookie 会话的建立与状态查询。 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final AccessKeyAuthenticator authenticator;
    private final boolean secureCookie;

    /**
     * 创建会话控制器。
     *
     * @param authenticator 会话签发与验证器
     * @param secureCookie HTTPS 部署时是否设置 Secure 属性
     */
    public SessionController(AccessKeyAuthenticator authenticator,
                             @Value("${music-ai.security.secure-cookie:false}") boolean secureCookie) {
        this.authenticator = authenticator;
        this.secureCookie = secureCookie;
    }

    /**
     * @param request HTTP 请求
     * @return 安全开关和当前会话认证状态
     */
    @GetMapping
    SessionStatus status(HttpServletRequest request) {
        boolean enabled = authenticator.enabled();
        boolean authenticated = !enabled || authenticator.isValidSessionToken(
                AccessKeyFilter.cookieValue(request, AccessKeyFilter.SESSION_COOKIE));
        return new SessionStatus(enabled, authenticated);
    }

    /**
     * 验证密钥并签发作用域为 /api 的 Strict HttpOnly cookie。
     * @param request 登录请求
     * @param response HTTP 响应
     * @return 已建立的会话状态；安全关闭时直接成功且不发 cookie
     */
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

    /**
     * @param accessKey 部署环境配置的项目访问密钥
     */
    public record LoginRequest(@NotBlank @Size(max = 512) String accessKey) {
    }

    /**
     * @param securityEnabled 是否启用访问保护
     * @param authenticated 当前请求是否已认证
     */
    public record SessionStatus(boolean securityEnabled, boolean authenticated) {
    }
}
