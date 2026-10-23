package com.byarrival.common.exception;

import org.springframework.validation.FieldError;

/**
 * @Valid 검증 실패 시 응답 data에 담기는 필드별 에러 정보
 */
public record FieldErrorResponse(String field, String reason) {

    public static FieldErrorResponse from(FieldError fieldError) {
        return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
