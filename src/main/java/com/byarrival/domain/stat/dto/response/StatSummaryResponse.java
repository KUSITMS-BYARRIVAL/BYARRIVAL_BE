package com.byarrival.domain.stat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record StatSummaryResponse(
    StatPeriod period,
    @Schema(description = "집계 시작 서비스일. ALL이면 null") LocalDate from,
    @Schema(description = "집계 끝 서비스일(오늘)") LocalDate to,
    @Schema(description = "총 이용시간(분). 완주한 노선의 출발~도착 시간 합, 노선마다 올림") long usedMinutes,
    @Schema(description = "읽은 기사 수([기사 보러가기]를 누른 기사)") long readArticleCount,
    @Schema(description = "완주 횟수") long completedCount,
    @Schema(description = "기록이 없는지. true면 Empty UI") boolean empty
) {
}
