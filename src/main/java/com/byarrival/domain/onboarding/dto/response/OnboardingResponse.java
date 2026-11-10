package com.byarrival.domain.onboarding.dto.response;

import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.TravelType;
import com.byarrival.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.List;

/**
 * 지금까지 저장한 온보딩 값. 앱이 중간에 이탈했다가 돌아오면 이 값으로 이어서 진행한다. 입력하지 않은 항목은 null이다.
 * jobGroups는 고른 순서대로 담긴다.
 */
public record OnboardingResponse(
    String nickname,
    TravelType travelType,
    @JsonFormat(pattern = "HH:mm") LocalTime commuteStartTime,
    Integer defaultCommuteMinutes,
    List<Long> categoryIds,
    List<JobGroup> jobGroups,
    boolean onboardingCompleted
) {

    public static OnboardingResponse of(User user, List<Long> categoryIds, List<JobGroup> jobGroups) {
        return new OnboardingResponse(user.getNickname(), user.getTravelType(), user.getCommuteStartTime(),
            user.getDefaultCommuteMinutes(), categoryIds, jobGroups, user.isOnboardingCompleted());
    }
}
