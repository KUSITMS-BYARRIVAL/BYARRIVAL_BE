package com.byarrival.domain.onboarding.dto.request;

import com.byarrival.domain.user.entity.TravelType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/**
 * 온보딩 화면마다 입력한 값만 보낸다. 보내지 않은(null) 항목은 바꾸지 않는다.
 *
 * @param commuteStartTime 이동 시작 시각 (HH:mm)
 * @param categoryIds      관심 분야. 보내면 이 목록으로 통째로 바꾸므로 1개 이상이어야 한다.
 */
public record OnboardingUpdateRequest(
    String nickname,
    TravelType travelType,
    @JsonFormat(pattern = "HH:mm") LocalTime commuteStartTime,
    Integer defaultCommuteMinutes,
    @Size(min = 1) List<@NotNull Long> categoryIds
) {
}
