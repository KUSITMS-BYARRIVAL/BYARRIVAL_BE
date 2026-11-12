package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PopularArticlesResponse(
    @Schema(description = "RANKED: 순위 표시, AGGREGATING: 그날 기사를 담은 사용자가 30명 미만이라 집계중 상태(순위·수치 미노출)")
    PopularArticlesStatus status,
    @Schema(description = "그날 노선에 많이 담은 순 최대 3건. 내가 이번 노선에서 읽은 기사는 빠진다. 집계중이면 빈 목록")
    List<PopularArticleResponse> articles
) {

    public static PopularArticlesResponse aggregating() {
        return new PopularArticlesResponse(PopularArticlesStatus.AGGREGATING, List.of());
    }

    public static PopularArticlesResponse ranked(List<PopularArticleResponse> articles) {
        return new PopularArticlesResponse(PopularArticlesStatus.RANKED, articles);
    }
}
