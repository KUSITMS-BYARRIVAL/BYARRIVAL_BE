package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record RouteStopResponse(
    Long routeId,
    @Schema(description = "정거장 번호 (1부터, 담은 순서)") int stopNumber,
    int totalStops,
    @Schema(description = "볼 수 있는 기사인지. false면 숨겼거나 원문이 삭제된 기사라 건너뛴다(아래 기사 내용은 null)")
    boolean available,
    Long articleId,
    String title,
    String pressName,
    @Schema(description = "기사 발행 시각") LocalDateTime publishedAt,
    Long categoryId,
    String categoryName,
    @Schema(description = "예상 읽기시간(분, 올림)") Integer readingMinutes,
    @Schema(description = "AI 핵심 키워드") List<String> keywords,
    @Schema(description = "AI 한 줄 요약") String oneLine,
    @Schema(description = "AI 3줄 요약") List<String> summary,
    @Schema(description = "원문 주소. [기사 보러가기]로 연다") String sourceUrl,
    @Schema(description = "완독했는지") boolean read,
    @Schema(description = "보관함에 저장했는지(READ-04 저장 토글)") boolean scrapped,
    RideProgressResponse progress
) {
}
