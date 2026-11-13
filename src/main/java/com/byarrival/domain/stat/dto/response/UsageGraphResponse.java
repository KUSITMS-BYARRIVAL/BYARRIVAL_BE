package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UsageGraphResponse(
    @Schema(description = "최근 6주, 오래된 주부터. 마지막이 이번 주") List<WeeklyUsageResponse> weeks,
    @Schema(description = "6주 동안 기록이 없는지. true면 초기 안내") boolean empty
) {
}
