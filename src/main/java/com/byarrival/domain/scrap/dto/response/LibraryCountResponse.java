package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탭별 저장 개수")
public record LibraryCountResponse(long article, long smalltalk) {
}
