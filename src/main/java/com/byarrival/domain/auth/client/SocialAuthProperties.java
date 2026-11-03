package com.byarrival.domain.auth.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 Provider 연동 설정
 */
@ConfigurationProperties(prefix = "auth.social")
public record SocialAuthProperties(
    Duration connectTimeout,
    Duration readTimeout,
    Kakao kakao,
    Apple apple
) {

    /**
     * @param appId 우리 서비스의 카카오 앱 ID. 다른 앱에서 발급된 토큰을 거부하는 데 쓴다.
     */
    public record Kakao(String tokenInfoUri, Long appId) {
    }

    /**
     * @param clientId identity token의 aud로 들어오는 앱 Bundle ID
     */
    public record Apple(String issuer, String keysUri, String clientId) {
    }
}
