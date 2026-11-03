package com.byarrival.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.client.SocialAuthClient;
import com.byarrival.domain.auth.client.SocialAuthClients;
import com.byarrival.domain.auth.dto.request.SocialLoginRequest;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String TOKEN = "social-token";
    private static final String PROVIDER_USER_ID = "12345";

    private SocialAuthClient kakaoClient;
    private UserService userService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        kakaoClient = mock(SocialAuthClient.class);
        SocialAuthClient appleClient = mock(SocialAuthClient.class);
        given(kakaoClient.provider()).willReturn(SocialProvider.KAKAO);
        given(appleClient.provider()).willReturn(SocialProvider.APPLE);
        userService = mock(UserService.class);
        authService = new AuthService(new SocialAuthClients(List.of(kakaoClient, appleClient)), userService);
    }

    @Test
    void 기존_회원이면_사용자_데이터를_불러오고_신규_회원이_아니다() {
        User user = userWithId(1L);
        given(kakaoClient.getProviderUserId(TOKEN)).willReturn(PROVIDER_USER_ID);
        given(userService.findActiveUser(SocialProvider.KAKAO, PROVIDER_USER_ID)).willReturn(Optional.of(user));

        SocialLoginResponse response = authService.socialLogin(new SocialLoginRequest(SocialProvider.KAKAO, TOKEN));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.isNewUser()).isFalse();
        verify(userService, never()).signUp(any(), any());
    }

    @Test
    void 신규_회원이면_계정을_생성하고_온보딩_미완료_상태로_응답한다() {
        User user = userWithId(2L);
        given(kakaoClient.getProviderUserId(TOKEN)).willReturn(PROVIDER_USER_ID);
        given(userService.findActiveUser(SocialProvider.KAKAO, PROVIDER_USER_ID)).willReturn(Optional.empty());
        given(userService.signUp(SocialProvider.KAKAO, PROVIDER_USER_ID)).willReturn(user);

        SocialLoginResponse response = authService.socialLogin(new SocialLoginRequest(SocialProvider.KAKAO, TOKEN));

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.onboardingCompleted()).isFalse();
    }

    @Test
    void 동시_가입으로_유니크_제약에_걸리면_먼저_가입된_회원으로_응답한다() {
        User user = userWithId(3L);
        given(kakaoClient.getProviderUserId(TOKEN)).willReturn(PROVIDER_USER_ID);
        given(userService.findActiveUser(SocialProvider.KAKAO, PROVIDER_USER_ID))
            .willReturn(Optional.empty(), Optional.of(user));
        given(userService.signUp(SocialProvider.KAKAO, PROVIDER_USER_ID))
            .willThrow(new DataIntegrityViolationException("uk_users_provider_active"));

        SocialLoginResponse response = authService.socialLogin(new SocialLoginRequest(SocialProvider.KAKAO, TOKEN));

        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    void 소셜_인증에_실패하면_회원을_조회하거나_생성하지_않는다() {
        given(kakaoClient.getProviderUserId(TOKEN)).willThrow(new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED));

        assertThatThrownBy(() -> authService.socialLogin(new SocialLoginRequest(SocialProvider.KAKAO, TOKEN)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.SOCIAL_AUTH_FAILED);
        verify(userService, never()).findActiveUser(any(), any());
        verify(userService, never()).signUp(any(), any());
    }

    private User userWithId(Long id) {
        User user = User.signUp(SocialProvider.KAKAO, PROVIDER_USER_ID);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
