package com.byarrival.domain.auth.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.security.JwtProperties;
import com.byarrival.common.security.JwtTokenProvider;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.entity.RefreshToken;
import com.byarrival.domain.auth.repository.RefreshTokenRepository;
import com.byarrival.domain.notification.service.DeviceTokenService;
import com.byarrival.domain.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 서비스 토큰 발급·재발급·폐기. 재발급 때마다 리프레시 토큰도 새로 발급하고 기존 토큰은 폐기한다(Rotation).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserService userService;
    private final DeviceTokenService deviceTokenService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TokenResponse issue(Long userId) {
        String refreshToken = generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(jwtProperties.refreshTokenValidity());
        refreshTokenRepository.save(RefreshToken.issue(userId, hash(refreshToken), expiresAt));
        return TokenResponse.of(jwtTokenProvider.createAccessToken(userId), refreshToken);
    }

    /**
     * 없거나 만료·폐기된 토큰, 탈퇴한 회원의 토큰이면 UNAUTHORIZED로 응답한다.
     */
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .filter(found -> found.isUsable(now))
            .orElseThrow(TokenService::unauthorized);
        // 같은 토큰으로 재발급이 동시에 들어오면 먼저 폐기한 요청만 통과한다.
        if (refreshTokenRepository.revokeIfActive(token.getId(), now) != 1) {
            throw unauthorized();
        }
        if (!userService.isActiveUser(token.getUserId())) {
            throw unauthorized();
        }
        return issue(token.getUserId());
    }

    /**
     * 현재 기기의 리프레시 토큰만 폐기한다. 이미 폐기됐거나 없는 토큰이어도 성공으로 처리한다.
     * fcmToken이 있으면 로그아웃한 기기로 푸시가 가지 않도록 그 기기 토큰도 지운다.
     */
    @Transactional
    public void logout(Long userId, String refreshToken, String fcmToken) {
        refreshTokenRepository.findByTokenHashAndUserId(hash(refreshToken), userId)
            .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), LocalDateTime.now(clock)));
        if (StringUtils.hasText(fcmToken)) {
            deviceTokenService.unregister(userId, fcmToken);
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private static BusinessException unauthorized() {
        return new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
