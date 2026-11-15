package com.byarrival.domain.route.dto.response;

import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.entity.RouteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RouteResponse(
    Long routeId,
    RouteStatus status,
    RouteCondition condition,
    LocalDate serviceDate,
    @Schema(description = "만료 시각 (서비스일 다음 날 04:00)") LocalDateTime expiresAt,
    @Schema(description = "목표 시간 T(분)") int targetMinutes,
    @Schema(description = "추천 폴백 단계 0/1/2") int fallbackStep,
    @Schema(description = "마지막으로 본 카드 순번. 없으면 null (첫 카드부터)") Integer lastViewedCardOrder,
    RouteCartResponse cart,
    @Schema(description = "후보 카드 풀 (카드 순번 순)") List<RouteCardResponse> cards,
    @Schema(description = "운행 진행 상태. 운행 중이거나 완주한 노선만 있고, 아니면 null (이어보기 RIDE-02)")
    RideProgressResponse ride
) {
}
