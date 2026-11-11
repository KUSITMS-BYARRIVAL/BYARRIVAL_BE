package com.byarrival.domain.onboarding.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OnboardingErrorCode implements ErrorCode {

    ONBOARDING_INCOMPLETE(HttpStatus.BAD_REQUEST, "온보딩 필수 정보가 모두 입력되지 않았습니다.");

    private final HttpStatus status;
    private final String message;
}
