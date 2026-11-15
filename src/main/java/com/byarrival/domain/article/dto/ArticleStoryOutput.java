package com.byarrival.domain.article.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 같은 사건 묶기 응답 JSON. 구조화 출력 스키마로도 쓴다. 값은 서버에서 검증한다.
 */
public record ArticleStoryOutput(
    @JsonPropertyDescription("같은 사건 기사 묶음. 2건 이상인 묶음만. 한 기사는 최대 한 묶음에만 들어간다")
    List<StoryGroup> groups
) {

    public record StoryGroup(
        @JsonPropertyDescription("묶은 사건을 10자 안팎으로")
        String event,

        @JsonPropertyDescription("입력받은 기사 id 그대로. 2개 이상")
        List<Long> articleIds
    ) {
    }
}
