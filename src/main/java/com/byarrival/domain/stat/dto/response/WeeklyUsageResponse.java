package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record WeeklyUsageResponse(
    @Schema(description = "주 시작(월요일)") LocalDate weekStart,
    @Schema(description = "주 끝(일요일)") LocalDate weekEnd,
    @Schema(description = "이용시간(분)") long usedMinutes,
    long readArticleCount,
    long completedCount
) {
}
