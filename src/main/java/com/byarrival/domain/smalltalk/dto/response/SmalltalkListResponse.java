package com.byarrival.domain.smalltalk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record SmalltalkListResponse(
    @Schema(description = "서비스일(04:00 경계)") LocalDate serviceDate,
    @Schema(description = "카테고리 화면 순서, 같은 카테고리는 인기 순서. 비어 있으면 Empty UI") List<SmalltalkResponse> smalltalks
) {
}
