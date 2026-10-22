package com.byarrival.common.exception;

import com.byarrival.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 전역 예외 처리. 4xx는 warn, 5xx는 error 로그를 남긴다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[BusinessException] {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage(),
                e);
        } else {
            log.warn("[BusinessException] {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        }
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.fail(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleValidationException(
        MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(FieldErrorResponse::from)
            .toList();
        log.warn("[ValidationException] {} {} - {}", request.getMethod(), request.getRequestURI(), fieldErrors);
        return toResponse(CommonErrorCode.INVALID_INPUT_VALUE, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException e, HttpServletRequest request) {
        return warnAndRespond(CommonErrorCode.INVALID_REQUEST_BODY, e, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException e, HttpServletRequest request) {
        return warnAndRespond(CommonErrorCode.MISSING_REQUEST_PARAMETER, e, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
        MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return warnAndRespond(CommonErrorCode.INVALID_TYPE_VALUE, e, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return warnAndRespond(CommonErrorCode.METHOD_NOT_ALLOWED, e, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
        NoResourceFoundException e, HttpServletRequest request) {
        return warnAndRespond(CommonErrorCode.RESOURCE_NOT_FOUND, e, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("[UnhandledException] {} {}", request.getMethod(), request.getRequestURI(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> warnAndRespond(
        ErrorCode errorCode, Exception e, HttpServletRequest request) {
        log.warn("[{}] {} {} - {}", e.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(),
            e.getMessage());
        return toResponse(errorCode, null);
    }

    private <T> ResponseEntity<ApiResponse<T>> toResponse(ErrorCode errorCode, T data) {
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.failWithData(errorCode, data));
    }
}
