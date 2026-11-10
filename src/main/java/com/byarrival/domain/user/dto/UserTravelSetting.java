package com.byarrival.domain.user.dto;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * 노선 생성에 필요한 사용자 설정. 기본 이동시간, 요일별 이동시간(0 = 쉬는 날), 관심 분야.
 */
public record UserTravelSetting(
    Integer defaultMinutes,
    Map<DayOfWeek, Integer> weekdayMinutes,
    List<Long> interestCategoryIds
) {

    public UserTravelSetting {
        weekdayMinutes = Map.copyOf(weekdayMinutes);
        interestCategoryIds = List.copyOf(interestCategoryIds);
    }

    /**
     * 그 요일의 이동시간을 우선 쓰고, 설정이 없거나 0분(쉬는 날)이면 기본 이동시간을 쓴다.
     *
     * @return 목표 시간(분). 기본 이동시간도 없으면 null
     */
    public Integer targetMinutes(DayOfWeek dayOfWeek) {
        Integer minutes = weekdayMinutes.get(dayOfWeek);
        if (minutes != null && minutes > 0) {
            return minutes;
        }
        return defaultMinutes;
    }
}
