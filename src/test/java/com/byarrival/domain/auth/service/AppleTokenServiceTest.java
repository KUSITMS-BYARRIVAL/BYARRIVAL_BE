package com.byarrival.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.byarrival.common.monitoring.ServerErrorNotifier;
import com.byarrival.common.security.TokenEncryptionProperties;
import com.byarrival.common.security.TokenEncryptor;
import com.byarrival.domain.auth.client.AppleTokenClient;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

class AppleTokenServiceTest {

    private static final Long USER_ID = 1L;

    private AppleTokenClient appleTokenClient;
    private TokenEncryptor tokenEncryptor;
    private UserService userService;
    private ServerErrorNotifier serverErrorNotifier;
    private AppleTokenService appleTokenService;

    @BeforeEach
    void setUp() {
        appleTokenClient = mock(AppleTokenClient.class);
        tokenEncryptor = new TokenEncryptor(
            new TokenEncryptionProperties(Base64.getEncoder().encodeToString(new byte[32])));
        userService = mock(UserService.class);
        serverErrorNotifier = mock(ServerErrorNotifier.class);
        appleTokenService = new AppleTokenService(appleTokenClient, tokenEncryptor, userService, serverErrorNotifier);
        given(appleTokenClient.isConfigured()).willReturn(true);
    }

    @Test
    void 로그인하면_Apple_refresh_token을_암호화해_저장한다() {
        given(appleTokenClient.exchangeRefreshToken("auth-code")).willReturn("apple-refresh");

        appleTokenService.storeRefreshToken(USER_ID, "auth-code");

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(userService).saveAppleRefreshToken(eq(USER_ID), saved.capture());
        assertThat(saved.getValue()).isNotEqualTo("apple-refresh");
        assertThat(tokenEncryptor.decrypt(saved.getValue())).isEqualTo("apple-refresh");
    }

    @Test
    void Apple_토큰_교환에_실패해도_예외를_던지지_않고_서버_에러_채널에_알린다() {
        RuntimeException failure = new ResourceAccessException("Apple 연결 실패");
        given(appleTokenClient.exchangeRefreshToken("auth-code")).willThrow(failure);

        appleTokenService.storeRefreshToken(USER_ID, "auth-code");

        verify(userService, never()).saveAppleRefreshToken(any(), any());
        verify(serverErrorNotifier).notify("POST", "/api/v1/auth/login", failure);
    }

    @Test
    void Apple_키나_암호화_키가_없으면_저장을_건너뛴다() {
        given(appleTokenClient.isConfigured()).willReturn(false);

        appleTokenService.storeRefreshToken(USER_ID, "auth-code");

        verify(appleTokenClient, never()).exchangeRefreshToken(any());
        verifyNoInteractions(userService, serverErrorNotifier);
    }

    @Test
    void 탈퇴하면_저장한_토큰을_복호화해_Apple_연결을_끊는다() {
        User user = appleUser(tokenEncryptor.encrypt("apple-refresh"));

        appleTokenService.revoke(user);

        verify(appleTokenClient).revoke("apple-refresh");
    }

    @Test
    void 저장된_Apple_토큰이_없으면_연결_끊기를_건너뛴다() {
        appleTokenService.revoke(appleUser(null));

        verify(appleTokenClient, never()).revoke(any());
    }

    @Test
    void Apple_연결_끊기에_실패하면_예외가_그대로_나간다() {
        willThrow(new ResourceAccessException("Apple 연결 실패")).given(appleTokenClient).revoke(any());

        assertThatThrownBy(() -> appleTokenService.revoke(appleUser(tokenEncryptor.encrypt("apple-refresh"))))
            .isInstanceOf(ResourceAccessException.class);
    }

    private User appleUser(String encryptedToken) {
        User user = User.signUp(SocialProvider.APPLE, "apple-sub");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.updateAppleRefreshToken(encryptedToken);
        return user;
    }
}
