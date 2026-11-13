package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record StreakResponse(
    @Schema(description = "연속 완주 일수. 오늘 아직 완주하지 않았으면 어제까지 이어진 일수") int currentStreak,
    @Schema(description = "최장 연속 완주 일수") int longestStreak
) {
}
