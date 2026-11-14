package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record LibraryResponse(
    LibraryType type,
    LibraryCountResponse counts,
    @Schema(description = "그 탭의 카테고리 필터 칩. 필터와 상관없이 그 탭에 저장한 콘텐츠 전체 기준") List<LibraryCategoryResponse> categories,
    @Schema(description = "기사 탭 목록. 다른 탭이면 빈 목록") List<LibraryArticleResponse> articles,
    @Schema(description = "스몰토크 탭 목록. 다른 탭이면 빈 목록") List<LibrarySmalltalkResponse> smalltalks
) {
}
