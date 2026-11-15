package com.byarrival.domain.quiz.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuizErrorCode implements ErrorCode {

    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "이 노선의 퀴즈가 아닙니다."),
    QUIZ_CHOICE_NOT_FOUND(HttpStatus.BAD_REQUEST, "없는 선택지입니다."),
    QUIZ_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "AI 퀴즈 응답이 규칙에 맞지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
