package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CalendarResponse(
    int year,
    @Schema(description = "오늘(KST 날짜). 진입 시 이 달을 가운데로 맞춘다") LocalDate today,
    @Schema(description = "1월 1일부터 12월 31일까지 모든 날짜") List<CalendarDayResponse> days
) {
}
