package com.byarrival.common.response;

import com.byarrival.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 모든 API의 공통 응답 형식 (성공/실패 모두 success, code, message, data 구조)
 */
@JsonPropertyOrder({"success", "code", "message", "data"})
public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null);
    }

    public static <T> ApiResponse<T> failWithData(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), data);
    }
}
