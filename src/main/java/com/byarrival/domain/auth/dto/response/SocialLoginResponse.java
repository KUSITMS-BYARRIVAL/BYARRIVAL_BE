package com.byarrival.domain.auth.dto.response;

import com.byarrival.domain.user.entity.User;

public record SocialLoginResponse(
    Long userId,
    boolean isNewUser,
    boolean onboardingCompleted
) {

    public static SocialLoginResponse of(User user, boolean isNewUser) {
        return new SocialLoginResponse(user.getId(), isNewUser, user.isOnboardingCompleted());
    }
}
