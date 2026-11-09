package com.byarrival.domain.article.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ArticleErrorCode implements ErrorCode {

    ARTICLE_CRAWL_FAILED(HttpStatus.BAD_GATEWAY, "기사 원문을 가져오지 못했습니다.");

    private final HttpStatus status;
    private final String message;
}
