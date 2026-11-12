package com.byarrival.domain.scrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record LibraryArticleResponse(
    @Schema(description = "보관함 항목 ID. 삭제·되돌리기에 쓴다") Long scrapId,
    Long articleId,
    @Schema(description = "볼 수 있는 기사인지. false면 숨겼거나 원문이 삭제된 기사라 비활성 카드로 보여 주고 원문으로 보내지 않는다(LIB-05)")
    boolean available,
    String title,
    String pressName,
    @Schema(description = "기사 발행 시각") LocalDateTime publishedAt,
    Long categoryId,
    String categoryName,
    @Schema(description = "AI 한 줄 요약") String oneLine,
    @Schema(description = "예상 읽기시간(분, 올림)") Integer readingMinutes,
    @Schema(description = "원문 주소. 볼 수 없는 기사면 null") String sourceUrl,
    @Schema(description = "저장한 시각") LocalDateTime scrappedAt
) {
}
