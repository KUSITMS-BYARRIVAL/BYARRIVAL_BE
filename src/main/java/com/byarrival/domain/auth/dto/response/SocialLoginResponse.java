package com.byarrival.domain.auth.dto.response;

import com.byarrival.domain.user.entity.User;

/**
 * @param termsAgreed 필수 약관에 모두 동의했는지. false면 앱은 약관 화면으로 이동한다.
 */
public record SocialLoginResponse(
    Long userId,
    boolean isNewUser,
    boolean termsAgreed,
    boolean onboardingCompleted,
    String accessToken,
    String refreshToken
) {

    public static SocialLoginResponse of(User user, boolean isNewUser, boolean termsAgreed, TokenResponse tokens) {
        return new SocialLoginResponse(user.getId(), isNewUser, termsAgreed, user.isOnboardingCompleted(),
            tokens.accessToken(), tokens.refreshToken());
    }
}
