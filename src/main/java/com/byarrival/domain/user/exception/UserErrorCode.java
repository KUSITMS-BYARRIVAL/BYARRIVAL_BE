package com.byarrival.domain.user.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 회원 정보 입력 규칙 위반. 온보딩과 마이 설정이 같은 규칙과 코드를 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임 형식이 올바르지 않습니다."),
    INVALID_COMMUTE_MINUTES(HttpStatus.BAD_REQUEST, "설정할 수 없는 이동시간입니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "선택할 수 없는 관심 분야입니다."),
    INVALID_WEEKLY_COMMUTE(HttpStatus.BAD_REQUEST, "요일별 이동 설정이 올바르지 않습니다."),
    NO_COMMUTE_DAY(HttpStatus.BAD_REQUEST, "이동하는 요일을 하루 이상 설정해야 합니다.");

    private final HttpStatus status;
    private final String message;
}
