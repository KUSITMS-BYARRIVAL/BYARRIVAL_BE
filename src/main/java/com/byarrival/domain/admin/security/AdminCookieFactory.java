package com.byarrival.domain.admin.security;

import com.byarrival.domain.admin.config.AdminAuthProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 어드민 토큰 쿠키. JS가 읽지 못하게 HttpOnly로 두고, 다른 사이트의 요청에는 붙지 않게 SameSite=Lax로 둔다.
 */
@Component
@RequiredArgsConstructor
public class AdminCookieFactory {

    public static final String COOKIE_NAME = "admin_access_token";
    private static final String COOKIE_PATH = "/api/admin";
    private static final String SAME_SITE = "Lax";

    private final AdminAuthProperties properties;

    public ResponseCookie create(String token) {
        return cookie(token, properties.sessionValidity());
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite(SAME_SITE)
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    }
}
