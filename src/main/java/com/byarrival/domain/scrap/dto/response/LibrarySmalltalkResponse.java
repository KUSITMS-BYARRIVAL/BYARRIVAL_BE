package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record LibrarySmalltalkResponse(
    @Schema(description = "보관함 항목 ID. 삭제·되돌리기에 쓴다") Long scrapId,
    Long smalltalkId,
    @Schema(description = "볼 수 있는지. false면 스몰토크나 연결 뉴스가 숨김·삭제돼 비활성으로 보여 준다") boolean available,
    @Schema(description = "한 줄") String hook,
    Long categoryId,
    String categoryName,
    @Schema(description = "연결 뉴스 제목") String articleTitle,
    @Schema(description = "연결 뉴스 원문 주소. 볼 수 없으면 null") String sourceUrl,
    LocalDateTime scrappedAt
) {
}
