package com.byarrival.domain.home.dto.response;

import com.byarrival.domain.route.dto.response.PopularArticleResponse;
import com.byarrival.domain.route.dto.response.PopularArticlesResponse;
import com.byarrival.domain.route.dto.response.PopularArticlesStatus;
import com.byarrival.domain.user.entity.JobGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 출근길 공감대 칩 하나의 순위(HOME-05·06·07).
 */
public record PopularChipResponse(
    @Schema(description = "직군 칩이면 직군 코드, 전체 칩이면 null") JobGroup jobGroup,
    @Schema(description = "RANKED: 순위 표시, AGGREGATING: 이 칩 범위에서 기사를 담은 사용자가 30명 미만이라 집계중 상태(순위·수치 미노출)")
    PopularArticlesStatus status,
    @Schema(description = "담은 사용자가 많은 순. 집계중이면 빈 목록") List<PopularArticleResponse> articles
) {

    public static PopularChipResponse of(JobGroup jobGroup, PopularArticlesResponse ranking) {
        return new PopularChipResponse(jobGroup, ranking.status(), ranking.articles());
    }
}
