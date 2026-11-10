package com.byarrival.domain.route.dto.request;

import com.byarrival.domain.route.entity.RouteCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RouteCreateRequest(
    @Schema(description = "오늘의 컨디션", example = "SUNNY")
    @NotNull RouteCondition condition
) {
}
