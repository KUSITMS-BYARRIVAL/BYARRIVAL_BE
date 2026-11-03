package com.byarrival.domain.user.dto.response;

import com.byarrival.domain.user.entity.User;

/**
 * 앱 진입 시 화면 분기(C-01)용 내 정보. 약관 미동의면 약관, 온보딩 미완료면 온보딩, 둘 다 완료면 홈으로 이동한다.
 */
public record UserMeResponse(
    Long userId,
    String nickname,
    String profileImageUrl,
    boolean termsAgreed,
    boolean onboardingCompleted
) {

    public static UserMeResponse of(User user, boolean termsAgreed) {
        return new UserMeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), termsAgreed,
            user.isOnboardingCompleted());
    }
}
