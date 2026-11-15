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
     * @param appId     우리 서비스의 카카오 앱 ID. 다른 앱에서 발급된 토큰을 거부하는 데 쓴다.
     * @param unlinkUri 회원 탈퇴 시 카카오 연결을 끊는 API
     * @param adminKey  연결 끊기에 쓰는 카카오 Admin 키. 비어 있으면 연결 끊기를 건너뛴다(로컬·테스트).
     */
    public record Kakao(String tokenInfoUri, Long appId, String unlinkUri, String adminKey) {
    }

    /**
     * @param clientId         identity token의 aud로 들어오는 앱 Bundle ID. Apple 토큰 요청의 client_id로도 쓴다.
     * @param tokenUri         authorization code를 refresh token으로 바꾸는 API
     * @param revokeUri        회원 탈퇴 시 Apple 연결을 끊는 API
     * @param teamId           Apple Developer Team ID
     * @param keyId            로그인용(Sign in with Apple) 키의 Key ID
     * @param privateKeyBase64 로그인용 .p8 키 파일을 base64로 인코딩한 값
     */
    public record Apple(String issuer, String keysUri, String clientId, String tokenUri, String revokeUri,
                        String teamId, String keyId, String privateKeyBase64) {
    }
}
