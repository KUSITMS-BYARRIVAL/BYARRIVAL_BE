package com.byarrival.domain.ai.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    AI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "AI API 호출에 실패했습니다."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 응답이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
