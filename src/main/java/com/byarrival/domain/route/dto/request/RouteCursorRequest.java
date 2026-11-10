package com.byarrival.domain.route.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RouteCursorRequest(
    @Schema(description = "마지막으로 본 후보 카드 순번 (1부터)", example = "3")
    @NotNull @Min(1) Integer cardOrder
) {
}
