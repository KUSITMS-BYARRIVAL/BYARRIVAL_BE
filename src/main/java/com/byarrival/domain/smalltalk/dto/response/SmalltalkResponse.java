package com.byarrival.domain.smalltalk.dto.response;

import com.byarrival.domain.smalltalk.service.SmalltalkQueryService.SmalltalkView;
import io.swagger.v3.oas.annotations.media.Schema;

public record SmalltalkResponse(
    Long smalltalkId,
    @Schema(description = "한 줄(hook)") String hook,
    Long categoryId,
    String categoryName,
    Long articleId,
    @Schema(description = "연결 뉴스 제목") String articleTitle,
    @Schema(description = "연결 뉴스 원문 주소. 연결 뉴스 영역을 누르면 연다") String sourceUrl,
    @Schema(description = "보관함에 저장했는지") boolean scrapped
) {

    public static SmalltalkResponse of(SmalltalkView view, boolean scrapped) {
        return new SmalltalkResponse(view.smalltalkId(), view.hook(), view.categoryId(), view.categoryName(),
            view.articleId(), view.articleTitle(), view.sourceUrl(), scrapped);
    }
}
