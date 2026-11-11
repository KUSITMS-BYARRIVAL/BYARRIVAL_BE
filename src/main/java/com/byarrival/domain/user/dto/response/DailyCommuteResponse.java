package com.byarrival.domain.user.dto.response;

import com.byarrival.domain.user.entity.DailyCommute;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record DailyCommuteResponse(
    DayOfWeek dayOfWeek,
    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    int minutes
) {

    public static DailyCommuteResponse of(DayOfWeek dayOfWeek, DailyCommute commute) {
        return new DailyCommuteResponse(dayOfWeek, commute.startTime(), commute.minutes());
    }
}
