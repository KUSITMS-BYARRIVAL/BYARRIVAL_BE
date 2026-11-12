package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record CalendarDayResponse(
    LocalDate date,
    CalendarLevel level,
    @Schema(description = "그날 노선에서 읽은 기사 수") long readCount,
    @Schema(description = "오늘 이후 날짜인지. true면 비활성으로 그린다") boolean future
) {
}
