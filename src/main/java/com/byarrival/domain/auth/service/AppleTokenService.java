package com.byarrival.domain.auth.service;

import com.byarrival.common.monitoring.ServerErrorNotifier;
import com.byarrival.common.security.TokenEncryptor;
import com.byarrival.domain.auth.client.AppleAuthClient;
import com.byarrival.domain.auth.client.AppleTokenClient;
import com.byarrival.domain.auth.client.AppleTokenClient.AppleTokens;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 탈퇴할 때 Apple 연결을 끊을 수 있도록 로그인 때 Apple refresh token을 받아 암호화해 저장하고, 탈퇴 때 revoke한다.
 * Apple 호출은 외부 API라 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleTokenService {

    static final String LOGIN_METHOD = "POST";
    static final String LOGIN_URI = "/api/v1/auth/login";

    private final AppleTokenClient appleTokenClient;
    private final AppleAuthClient appleAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final UserService userService;
    private final ServerErrorNotifier serverErrorNotifier;

    /**
     * 실패해도 로그인은 그대로 진행한다. 그 회원은 다음 로그인 때 다시 저장되고, 그 전에 탈퇴하면 revoke만 건너뛴다.
     * code로 받은 id token의 Apple 계정(sub)이 로그인한 회원과 다르면 다른 계정의 토큰이므로 저장하지 않는다.
     */
    public void storeRefreshToken(Long userId, String providerUserId, String authorizationCode) {
        if (!isAvailable()) {
            log.info("Apple 키 또는 토큰 암호화 키가 없어 Apple 토큰 저장을 건너뜀");
            return;
        }
        try {
            AppleTokens tokens = appleTokenClient.exchangeRefreshToken(authorizationCode);
            if (!providerUserId.equals(appleAuthClient.getProviderUserId(tokens.idToken()))) {
                throw new IllegalStateException("authorization code의 Apple 계정이 로그인한 회원과 다릅니다.");
            }
            userService.saveAppleRefreshToken(userId, tokenEncryptor.encrypt(tokens.refreshToken()));
        } catch (RuntimeException e) {
            log.error("Apple 토큰 저장 실패. 탈퇴 전에 다시 로그인하면 저장됩니다: userId={}, error={}", userId,
                e.getClass().getSimpleName());
            serverErrorNotifier.notify(LOGIN_METHOD, LOGIN_URI, e);
        }
    }

    /**
     * 저장된 Apple 토큰이 없거나(이 기능 전에 가입, 저장 실패) 설정이 없으면 건너뛴다.
     * Apple이 실패 응답을 주거나 호출에 실패하면 예외가 그대로 나간다.
     */
    public void revoke(User user) {
        if (!StringUtils.hasText(user.getAppleRefreshToken())) {
            log.info("저장된 Apple 토큰이 없어 Apple 연결 끊기를 건너뜀: userId={}", user.getId());
            return;
        }
        if (!isAvailable()) {
            log.info("Apple 키 또는 토큰 암호화 키가 없어 Apple 연결 끊기를 건너뜀: userId={}", user.getId());
            return;
        }
        appleTokenClient.revoke(tokenEncryptor.decrypt(user.getAppleRefreshToken()));
    }

    private boolean isAvailable() {
        return appleTokenClient.isConfigured() && tokenEncryptor.isEnabled();
    }
}
