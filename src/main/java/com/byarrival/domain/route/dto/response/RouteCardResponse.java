package com.byarrival.domain.route.dto.response;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;

public record RouteCardResponse(
    @Schema(description = "후보 카드 순번 (1부터)") int cardOrder,
    Long articleId,
    String title,
    Long categoryId,
    String categoryName,
    String pressName,
    ArticleDifficulty difficulty,
    @Schema(description = "예상 읽기시간(분, 올림)") int readingMinutes,
    @Schema(description = "AI 한 줄 요약") String oneLine,
    @Schema(description = "추천 구성 기사 여부 (아니면 보충 후보)") boolean recommended,
    @Schema(description = "담김 여부") boolean carted,
    @Schema(description = "담은 순서 (1부터). 담지 않았으면 null") Integer cartOrder,
    @Schema(description = "담을 수 있는지. 담으면 최대 시간을 넘는 카드는 false") boolean addable
) {
}
