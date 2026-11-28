package com.byarrival.domain.notification.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NO_REGISTERED_DEVICE(HttpStatus.BAD_REQUEST, "푸시를 받을 기기가 등록되어 있지 않습니다."),
    BROADCAST_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    BROADCAST_NOT_EDITABLE(HttpStatus.CONFLICT, "발송을 시작했거나 취소한 알림은 바꿀 수 없습니다."),
    BROADCAST_NO_RECIPIENT(HttpStatus.CONFLICT, "받을 사람이 없어 보낼 수 없습니다."),
    BROADCAST_PAST_SCHEDULE(HttpStatus.BAD_REQUEST, "예약 시각은 지금보다 뒤여야 합니다."),
    BROADCAST_INVALID_LINK(HttpStatus.BAD_REQUEST, "공지사항 화면은 https 주소가 필요하고, 다른 화면에는 주소를 넣지 않습니다."),
    BROADCAST_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다. 최대 92일까지 볼 수 있습니다."),
    BROADCAST_NO_TEST_TARGET(HttpStatus.CONFLICT, "테스트 발송을 받을 회원이 설정되어 있지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
