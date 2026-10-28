package com.duemet.identity.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Session tokens live in httpOnly cookies so page scripts (and any XSS) cannot read them.
 * The refresh cookie is scoped to /api/auth so it is only sent to the refresh/logout endpoints.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "duemet_at";
    public static final String REFRESH = "duemet_rt";
    private static final String REFRESH_PATH = "/api/auth";

    private final long accessMs;
    private final long refreshMs;
    private final boolean secure;

    public AuthCookies(
            @Value("${app.security.jwt.expiration-ms:900000}") long accessMs,
            @Value("${app.security.jwt.refresh-expiration-ms:604800000}") long refreshMs,
            @Value("${app.security.cookie-secure:false}") boolean secure) {
        this.accessMs = accessMs;
        this.refreshMs = refreshMs;
        this.secure = secure;
    }

    public void write(HttpServletResponse response, String accessToken, String refreshToken) {
        add(response, build(ACCESS, accessToken, "/", Duration.ofMillis(accessMs)));
        add(response, build(REFRESH, refreshToken, REFRESH_PATH, Duration.ofMillis(refreshMs)));
    }

    public void clear(HttpServletResponse response) {
        add(response, build(ACCESS, "", "/", Duration.ZERO));
        add(response, build(REFRESH, "", REFRESH_PATH, Duration.ZERO));
    }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
