package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ArrivalResponse(
    Long routeId,
    @Schema(description = "읽은 기사 수") int readCount,
    @Schema(description = "전체 정거장 수") int totalStops,
    @Schema(description = "연속 이용 일수 (완주한 평일이 하루도 빠짐없이 이어진 일수, 주말은 건너뜀, 오늘 포함)") int streakDays,
    @Schema(description = "이용시간(분, 출발부터 도착까지, 올림)") long usedMinutes,
    LocalDateTime completedAt,
    @Schema(description = "오늘 다른 사람들이 많이 담은 뉴스 TOP 3. 집계에 실패하면 null이며 섹션을 숨긴다")
    PopularArticlesResponse top3
) {
}
