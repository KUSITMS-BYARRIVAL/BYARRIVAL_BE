package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScrapStatusResponse(
    @Schema(description = "저장한 콘텐츠 ID (기사 ID 등)") Long targetId,
    @Schema(description = "보관함에 저장돼 있는지") boolean scrapped
) {
}
