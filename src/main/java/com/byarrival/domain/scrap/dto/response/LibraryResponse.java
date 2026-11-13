package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record LibraryResponse(
    LibraryType type,
    LibraryCountResponse counts,
    @Schema(description = "기사 탭의 카테고리 필터 칩. 필터와 상관없이 저장 기사 전체 기준") List<LibraryCategoryResponse> categories,
    @Schema(description = "기사 탭 목록. 다른 탭이면 빈 목록") List<LibraryArticleResponse> articles
) {
}
