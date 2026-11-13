package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컨디션별 완주한 날 수")
public record ConditionCountResponse(
    @Schema(description = "맑음") long sunny,
    @Schema(description = "보통") long normal,
    @Schema(description = "흐림") long cloudy,
    @Schema(description = "비") long rainy
) {
}
