package com.byarrival.domain.auth.service;

import com.byarrival.domain.auth.client.SocialAuthClients;
import com.byarrival.domain.auth.dto.request.SocialLoginRequest;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인·가입. 소셜 토큰 검증은 트랜잭션 밖에서 하고 회원 조회·생성만 UserService 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialAuthClients socialAuthClients;
    private final UserService userService;

    public SocialLoginResponse socialLogin(SocialLoginRequest request) {
        SocialProvider provider = request.provider();
        String providerUserId = socialAuthClients.get(provider).getProviderUserId(request.token());

        return userService.findActiveUser(provider, providerUserId)
            .map(user -> SocialLoginResponse.of(user, false))
            .orElseGet(() -> signUp(provider, providerUserId));
    }

    // 같은 계정의 가입 요청이 동시에 들어와 먼저 가입된 경우 기존 회원으로 응답한다.
    private SocialLoginResponse signUp(SocialProvider provider, String providerUserId) {
        try {
            return SocialLoginResponse.of(userService.signUp(provider, providerUserId), true);
        } catch (DataIntegrityViolationException e) {
            return userService.findActiveUser(provider, providerUserId)
                .map(user -> SocialLoginResponse.of(user, false))
                .orElseThrow(() -> e);
        }
    }
}
