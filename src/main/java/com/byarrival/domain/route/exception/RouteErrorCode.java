package com.byarrival.domain.route.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RouteErrorCode implements ErrorCode {

    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "노선을 찾을 수 없습니다."),
    ROUTE_EXPIRED(HttpStatus.CONFLICT, "만료된 노선입니다."),
    ROUTE_INVALID_STATUS(HttpStatus.CONFLICT, "지금 노선 상태에서는 할 수 없는 요청입니다."),
    ROUTE_CARD_NOT_IN_POOL(HttpStatus.NOT_FOUND, "노선의 후보 카드가 아닙니다."),
    ROUTE_CART_OVER_LIMIT(HttpStatus.BAD_REQUEST, "분량이 넘어요."),
    ROUTE_NOT_STARTABLE(HttpStatus.BAD_REQUEST, "담은 카드가 시작 조건을 만족하지 않습니다."),
    ROUTE_TRAVEL_TIME_NOT_SET(HttpStatus.CONFLICT, "이동시간 설정이 필요합니다.");

    private final HttpStatus status;
    private final String message;
}
