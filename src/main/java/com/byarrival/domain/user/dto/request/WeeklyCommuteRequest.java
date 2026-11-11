package com.byarrival.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 요일별 이동 설정. 이동하는 요일만 보내고, 보내지 않은 요일은 이동하지 않는 날(출근 알림 없음)이 된다.
 */
public record WeeklyCommuteRequest(
    @NotNull List<@NotNull @Valid DailyCommuteRequest> days
) {

    /**
     * @param startTime 이동 시작 시각 (HH:mm)
     * @param minutes   이동시간(분). 5~120분, 5분 단위
     */
    public record DailyCommuteRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull Integer minutes
    ) {
    }
}
