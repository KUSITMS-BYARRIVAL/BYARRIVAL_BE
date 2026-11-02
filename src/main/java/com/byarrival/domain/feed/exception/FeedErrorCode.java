package com.byarrival.domain.feed.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    FEED_NOT_FOUND(HttpStatus.NOT_FOUND, "RSS Feed를 찾을 수 없습니다."),
    DUPLICATE_FEED_URL(HttpStatus.CONFLICT, "이미 등록된 Feed URL입니다."),
    INVALID_FEED_URL(HttpStatus.BAD_REQUEST, "사용할 수 없는 Feed URL입니다.");

    private final HttpStatus status;
    private final String message;
}
