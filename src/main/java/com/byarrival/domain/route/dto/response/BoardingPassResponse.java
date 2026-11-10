package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BoardingPassResponse(
    Long routeId,
    @Schema(description = "담은 기사 수") int articleCount,
    @Schema(description = "예상 소요시간(분). 소수 합을 올림") int totalMinutes,
    @Schema(description = "목표 시간 T(분)") int targetMinutes,
    @Schema(description = "담은 카드 (담은 순서)") List<RouteCardResponse> cards
) {
}
