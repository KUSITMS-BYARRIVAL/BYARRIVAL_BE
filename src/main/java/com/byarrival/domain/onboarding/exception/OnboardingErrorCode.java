package com.byarrival.domain.onboarding.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OnboardingErrorCode implements ErrorCode {

    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임 형식이 올바르지 않습니다."),
    INVALID_COMMUTE_MINUTES(HttpStatus.BAD_REQUEST, "설정할 수 없는 이동시간입니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "선택할 수 없는 관심 분야입니다."),
    ONBOARDING_INCOMPLETE(HttpStatus.BAD_REQUEST, "온보딩 필수 정보가 모두 입력되지 않았습니다.");

    private final HttpStatus status;
    private final String message;
}
