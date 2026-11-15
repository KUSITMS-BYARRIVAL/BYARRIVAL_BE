package com.byarrival.domain.user.dto.response;

import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.TravelType;
import com.byarrival.domain.user.entity.User;
import java.util.List;

/**
 * 내 정보. 앱 진입 시 화면 분기(약관 미동의면 약관, 온보딩 미완료면 온보딩, 둘 다 완료면 홈)와 마이 화면에 쓴다.
 * 입력하지 않은 항목은 null이다. 출근 시각은 weeklyCommute에 요일별로 담기며, 이동하는 요일만 월요일부터 순서대로 담긴다.
 *
 * @param defaultCommuteMinutes 기본 이동시간. 요일별 설정이 없는 날의 노선 목표 시간으로 쓴다.
 */
public record UserMeResponse(
    Long userId,
    String nickname,
    String profileImageUrl,
    boolean termsAgreed,
    boolean onboardingCompleted,
    TravelType travelType,
    Integer defaultCommuteMinutes,
    List<DailyCommuteResponse> weeklyCommute,
    List<Long> categoryIds,
    List<JobGroup> jobGroups
) {

    public static UserMeResponse of(User user, boolean termsAgreed, List<Long> categoryIds, List<JobGroup> jobGroups) {
        List<DailyCommuteResponse> weeklyCommute = user.getWeeklyCommute().entrySet().stream()
            .map(entry -> DailyCommuteResponse.of(entry.getKey(), entry.getValue()))
            .toList();
        return new UserMeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), termsAgreed,
            user.isOnboardingCompleted(), user.getTravelType(), user.getDefaultCommuteMinutes(), weeklyCommute,
            categoryIds, jobGroups);
    }
}
