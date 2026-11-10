package com.byarrival.domain.article.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 난이도 판정 응답 JSON. 구조화 출력 스키마로도 쓴다. 값은 서버에서 검증한다.
 */
public record ArticleDifficultyOutput(
    @JsonPropertyDescription("입력받은 기사마다 하나씩")
    List<Score> scores
) {

    public record Score(
        @JsonPropertyDescription("입력받은 기사 id 그대로")
        Long articleId,

        @JsonPropertyDescription("일반 직장인 독자 기준 이해 난이도. 1(쉬움)~10(어려움) 정수")
        Integer score
    ) {
    }
}
