package com.byarrival.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.security.JwtProperties;
import com.byarrival.common.security.JwtTokenProvider;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.entity.RefreshToken;
import com.byarrival.domain.auth.repository.RefreshTokenRepository;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final Long USER_ID = 1L;

    private RefreshTokenRepository refreshTokenRepository;
    private UserService userService;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userService = mock(UserService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        given(jwtTokenProvider.createAccessToken(anyLong())).willReturn("access-token");
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        JwtProperties properties = new JwtProperties("secret", Duration.ofHours(24), Duration.ofHours(24));
        tokenService = new TokenService(refreshTokenRepository, jwtTokenProvider, properties, userService, clock);
    }

    @Test
    void 발급하면_리프레시_토큰은_원문_대신_해시로_만료_시각과_함께_저장한다() {
        TokenResponse response = tokenService.issue(USER_ID);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(response.refreshToken());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusHours(24));
    }

    @Test
    void 재발급하면_기존_토큰을_폐기하고_새_토큰을_발급한다() {
        RefreshToken token = storedToken(NOW.plusHours(1), null);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
        given(refreshTokenRepository.revokeIfActive(10L, NOW)).willReturn(1);
        given(userService.isActiveUser(USER_ID)).willReturn(true);

        TokenResponse response = tokenService.reissue("refresh-token");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotEqualTo("refresh-token");
        verify(refreshTokenRepository).revokeIfActive(10L, NOW);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void 저장되지_않은_리프레시_토큰이면_UNAUTHORIZED다() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertUnauthorized("unknown");
    }

    @Test
    void 만료된_리프레시_토큰이면_UNAUTHORIZED이고_폐기를_시도하지_않는다() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
            .willReturn(Optional.of(storedToken(NOW, null)));

        assertUnauthorized("expired");
        verify(refreshTokenRepository, never()).revokeIfActive(anyLong(), any());
    }

    @Test
    void 이미_폐기된_리프레시_토큰이면_UNAUTHORIZED다() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
            .willReturn(Optional.of(storedToken(NOW.plusHours(1), NOW.minusMinutes(1))));

        assertUnauthorized("revoked");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 동시_재발급에서_먼저_폐기된_토큰이면_UNAUTHORIZED다() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
            .willReturn(Optional.of(storedToken(NOW.plusHours(1), null)));
        given(refreshTokenRepository.revokeIfActive(10L, NOW)).willReturn(0);

        assertUnauthorized("refresh-token");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 탈퇴한_회원의_리프레시_토큰이면_UNAUTHORIZED다() {
        given(refreshTokenRepository.findByTokenHash(anyString()))
            .willReturn(Optional.of(storedToken(NOW.plusHours(1), null)));
        given(refreshTokenRepository.revokeIfActive(10L, NOW)).willReturn(1);
        given(userService.isActiveUser(USER_ID)).willReturn(false);

        assertUnauthorized("refresh-token");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 로그아웃하면_현재_기기의_리프레시_토큰만_폐기한다() {
        given(refreshTokenRepository.findByTokenHashAndUserId(anyString(), eq(USER_ID)))
            .willReturn(Optional.of(storedToken(NOW.plusHours(1), null)));

        tokenService.logout(USER_ID, "refresh-token");

        verify(refreshTokenRepository).revokeIfActive(10L, NOW);
    }

    @Test
    void 로그아웃할_토큰이_없어도_성공한다() {
        given(refreshTokenRepository.findByTokenHashAndUserId(anyString(), eq(USER_ID))).willReturn(Optional.empty());

        tokenService.logout(USER_ID, "unknown");

        verify(refreshTokenRepository, never()).revokeIfActive(anyLong(), any());
    }

    private RefreshToken storedToken(LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken token = RefreshToken.issue(USER_ID, "hash", expiresAt);
        ReflectionTestUtils.setField(token, "id", 10L);
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        return token;
    }

    private void assertUnauthorized(String refreshToken) {
        assertThatThrownBy(() -> tokenService.reissue(refreshToken))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
