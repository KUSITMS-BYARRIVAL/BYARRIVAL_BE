package com.byarrival.domain.notification.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NO_REGISTERED_DEVICE(HttpStatus.BAD_REQUEST, "푸시를 받을 기기가 등록되어 있지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
