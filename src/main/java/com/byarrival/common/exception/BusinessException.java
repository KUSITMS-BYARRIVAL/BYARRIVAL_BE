package com.byarrival.common.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외. 예: throw new BusinessException(UserErrorCode.USER_NOT_FOUND)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
