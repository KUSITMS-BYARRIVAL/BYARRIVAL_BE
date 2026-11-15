package com.byarrival.domain.auth.service;

import com.byarrival.domain.auth.client.SocialAuthClients;
import com.byarrival.domain.auth.dto.request.SocialLoginRequest;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.term.service.TermService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인·가입. 소셜 토큰 검증은 트랜잭션 밖에서 하고 회원 조회·생성만 UserService 트랜잭션으로 처리한다.
 * 로그인에 성공하면 서비스 토큰을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialAuthClients socialAuthClients;
    private final UserService userService;
    private final TokenService tokenService;
    private final TermService termService;
    private final AppleTokenService appleTokenService;

    public SocialLoginResponse socialLogin(SocialLoginRequest request) {
        SocialProvider provider = request.provider();
        String providerUserId = socialAuthClients.get(provider).getProviderUserId(request.token());

        LoginUser loginUser = userService.findActiveUser(provider, providerUserId)
            .map(user -> new LoginUser(user, false))
            .or(() -> restore(provider, providerUserId))
            .orElseGet(() -> signUp(provider, providerUserId));
        User user = loginUser.user();
        if (provider == SocialProvider.APPLE && StringUtils.hasText(request.authorizationCode())) {
            appleTokenService.storeRefreshToken(user.getId(), request.authorizationCode());
        }
        return SocialLoginResponse.of(user, loginUser.isNewUser(), termService.hasAgreedRequiredTerms(user.getId()),
            tokenService.issue(user.getId()));
    }

    // 보관 기간 안에 탈퇴한 계정이면 다시 활성화해 기존 회원으로 응답한다. 동시에 가입·복원된 경우 그 회원으로 응답한다.
    private Optional<LoginUser> restore(SocialProvider provider, String providerUserId) {
        try {
            return userService.restoreWithdrawnUser(provider, providerUserId).map(user -> new LoginUser(user, false));
        } catch (DataIntegrityViolationException e) {
            return userService.findActiveUser(provider, providerUserId).map(user -> new LoginUser(user, false));
        }
    }

    // 같은 계정의 가입 요청이 동시에 들어와 먼저 가입된 경우 기존 회원으로 응답한다.
    private LoginUser signUp(SocialProvider provider, String providerUserId) {
        try {
            return new LoginUser(userService.signUp(provider, providerUserId), true);
        } catch (DataIntegrityViolationException e) {
            return userService.findActiveUser(provider, providerUserId)
                .map(user -> new LoginUser(user, false))
                .orElseThrow(() -> e);
        }
    }

    private record LoginUser(User user, boolean isNewUser) {
    }
}
