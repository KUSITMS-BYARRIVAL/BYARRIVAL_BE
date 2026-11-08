package com.byarrival.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 토큰(JWT 액세스 토큰, 리프레시 토큰) 설정
 *
 * @param secret               HS256 서명 키. 32바이트 이상이어야 한다.
 * @param accessTokenValidity  액세스 토큰 유효기간
 * @param refreshTokenValidity 리프레시 토큰 유효기간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    Duration accessTokenValidity,
    Duration refreshTokenValidity
) {
}
