package com.byarrival.domain.scrap.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScrapErrorCode implements ErrorCode {

    SCRAP_NOT_FOUND(HttpStatus.NOT_FOUND, "보관함에 없는 콘텐츠입니다."),
    SCRAP_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "저장할 수 없는 콘텐츠입니다.");

    private final HttpStatus status;
    private final String message;
}
