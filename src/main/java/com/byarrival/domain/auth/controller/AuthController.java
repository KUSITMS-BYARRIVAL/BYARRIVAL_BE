package com.byarrival.domain.auth.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.auth.dto.request.LogoutRequest;
import com.byarrival.domain.auth.dto.request.RefreshTokenRequest;
import com.byarrival.domain.auth.dto.request.SocialLoginRequest;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.AuthService;
import com.byarrival.domain.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인·토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @Operation(summary = "소셜 로그인", description = "카카오 access token 또는 애플 identity token으로 로그인·가입하고 서비스 토큰을 발급한다.")
    @PostMapping("/login")
    public ApiResponse<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.ok(authService.socialLogin(request));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스 토큰과 리프레시 토큰을 새로 발급한다. 기존 리프레시 토큰은 폐기된다.")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(tokenService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "현재 기기의 리프레시 토큰을 폐기한다. fcmToken을 함께 보내면 이 기기의 푸시 토큰도 지운다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@LoginUserId Long userId, @Valid @RequestBody LogoutRequest request) {
        tokenService.logout(userId, request.refreshToken(), request.fcmToken());
        return ApiResponse.ok();
    }
}
