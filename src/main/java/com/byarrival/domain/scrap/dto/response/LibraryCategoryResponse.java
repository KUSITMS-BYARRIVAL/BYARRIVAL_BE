package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 필터 칩. 저장 기사가 있는 카테고리만 화면 순서대로 담는다(0건 카테고리 숨김)")
public record LibraryCategoryResponse(Long categoryId, String name, long count) {
}
