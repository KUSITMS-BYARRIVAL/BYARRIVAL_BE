package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PopularArticleResponse(
    @Schema(description = "순위 (1부터)") int rank,
    Long articleId,
    String title,
    String pressName,
    Long categoryId,
    String categoryName,
    @Schema(description = "예상 읽기시간(분, 올림)") Integer readingMinutes,
    @Schema(description = "그날 이 기사를 노선에 담은 사용자 수") long cartedUserCount,
    @Schema(description = "원문 주소. 열어도 완독·이용 기록에 넣지 않는다") String url
) {
}
