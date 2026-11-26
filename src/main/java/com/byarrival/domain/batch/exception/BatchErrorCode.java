package com.byarrival.domain.batch.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BatchErrorCode implements ErrorCode {

    BATCH_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "배치 실행 이력을 찾을 수 없습니다."),
    BATCH_RUNNING(HttpStatus.CONFLICT, "배치가 실행 중입니다. 끝난 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
