package com.byarrival.domain.term.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermErrorCode implements ErrorCode {

    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해야 합니다."),
    INVALID_TERMS(HttpStatus.BAD_REQUEST, "동의할 수 없는 약관이 포함되어 있습니다.");

    private final HttpStatus status;
    private final String message;
}
