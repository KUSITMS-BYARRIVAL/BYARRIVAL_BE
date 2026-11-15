package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record StopReadResponse(
    int stopNumber,
    Long articleId,
    @Schema(description = "완독 처리됨. 원문 열기에 실패해도 완독이다") boolean read,
    RideProgressResponse progress
) {
}
