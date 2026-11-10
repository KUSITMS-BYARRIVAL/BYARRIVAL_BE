package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RouteCartResponse(
    @Schema(description = "담은 카드 수") int count,
    @Schema(description = "담은 카드 예상 읽기시간 합계(분). 소수 합을 올림") int totalMinutes,
    @Schema(description = "시작하기 최소 시간(분)") int minMinutes,
    @Schema(description = "담을 수 있는 최대 시간(분)") int maxMinutes,
    @Schema(description = "시작하기 활성 여부") boolean startable,
    @Schema(description = "더 담을 수 있는 카드가 없음 ('더 담을 수 있는 카드가 없어요' 안내)") boolean noMoreCards,
    @Schema(description = "담은 기사 ID (담은 순서)") List<Long> articleIds
) {
}
