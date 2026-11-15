package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CalendarResponse(
    int year,
    @Schema(description = "오늘(KST 날짜). 이 날 이후는 future=true") LocalDate today,
    @Schema(description = "그 해 평일(월~금) 중 노선을 완주한(초록3) 날 수. \"올해 N일 완주했어요\"") int completedDays,
    @Schema(description = "그 해 1월 1일부터 12월 31일까지의 평일(월~금)만 날짜순으로. 토·일은 담지 않는다") List<CalendarDayResponse> days
) {
}
