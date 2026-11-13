package com.byarrival.domain.stat.dto.response;

import com.byarrival.domain.route.entity.RouteCondition;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConditionStatResponse(
    int year,
    @Schema(description = "올해 완주한 날의 컨디션 분포(막대·범례)") ConditionCountResponse yearCounts,
    @Schema(description = "이번 달 완주한 날의 컨디션 분포") ConditionCountResponse monthCounts,
    @Schema(description = "이번 달 가장 많았던 컨디션(\"이번 달은 맑은 날이 가장 많았어요\"). 이번 달 완주가 없으면 null, 동률이면 맑음>보통>흐림>비 순")
    RouteCondition monthTopCondition
) {
}
