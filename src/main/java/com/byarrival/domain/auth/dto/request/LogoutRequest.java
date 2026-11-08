package com.byarrival.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그아웃 요청. fcmToken을 함께 보내면 이 기기의 푸시 토큰도 지운다.
 */
public record LogoutRequest(
    @NotBlank String refreshToken,
    @Size(max = 512) String fcmToken
) {
}
