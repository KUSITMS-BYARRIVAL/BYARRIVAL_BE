package com.byarrival.domain.auth.dto.request;

import com.byarrival.domain.user.entity.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param token             카카오는 access token, 애플은 identity token
 * @param authorizationCode 애플만. 로그인 SDK가 identity token과 함께 주는 값이다. 탈퇴 시 Apple 연결 끊기에 쓰는 토큰을 받는 데 쓰며,
 *                          없어도 로그인은 된다.
 */
public record SocialLoginRequest(
    @NotNull SocialProvider provider,
    @NotBlank String token,
    @Size(max = 1024) String authorizationCode
) {

    public SocialLoginRequest(SocialProvider provider, String token) {
        this(provider, token, null);
    }
}
