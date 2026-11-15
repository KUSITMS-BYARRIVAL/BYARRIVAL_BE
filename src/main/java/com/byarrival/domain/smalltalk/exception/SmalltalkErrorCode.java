package com.byarrival.domain.smalltalk.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SmalltalkErrorCode implements ErrorCode {

    SMALLTALK_NOT_FOUND(HttpStatus.NOT_FOUND, "삭제되었거나 볼 수 없는 스몰토크입니다."),
    SMALLTALK_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AI 스몰토크 응답이 규칙에 맞지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
