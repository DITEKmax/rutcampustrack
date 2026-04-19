package ru.rutcampustrack.auth.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * M03b Группа 2: refresh-token cookie (HttpOnly+Secure+SameSite=Strict).
 * Path=/api/auth — cookie видна всем auth endpoint'ам (login/refresh/logout)
 * и ничему больше. Решение из OWNER-ANSWERS 02-Q-frontend-security +
 * DECISIONS 2026-04-20.
 */
public final class AuthCookies {

    public static final String REFRESH_COOKIE_NAME = "rct_refresh";
    public static final String COOKIE_PATH = "/api/auth";

    private AuthCookies() {
    }

    public static ResponseCookie issue(String refreshToken, long maxAgeSeconds) {
        return baseBuilder()
                .value(refreshToken)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    public static ResponseCookie clear() {
        return baseBuilder()
                .value("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseBuilder() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
