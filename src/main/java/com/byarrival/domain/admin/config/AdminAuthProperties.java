package com.byarrival.domain.admin.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 어드민 로그인 설정.
 *
 * @param sessionValidity  로그인 유지 시간(토큰과 쿠키의 유효기간)
 * @param cookieSecure     HTTPS에서만 쿠키를 보낸다
 * @param allowedOrigins   쿠키를 함께 보낼 수 있는 어드민 웹 주소
 */
@ConfigurationProperties(prefix = "admin.auth")
public record AdminAuthProperties(
    Duration sessionValidity,
    boolean cookieSecure,
    List<String> allowedOrigins
) {

    public AdminAuthProperties {
        if (sessionValidity == null || !sessionValidity.isPositive()) {
            throw new IllegalArgumentException("admin.auth.session-validity는 0보다 커야 합니다.");
        }
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("admin.auth.allowed-origins에는 정확한 주소만 넣을 수 있습니다.");
        }
    }
}
