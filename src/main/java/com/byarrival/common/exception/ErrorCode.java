package com.byarrival.common.exception;

import org.springframework.http.HttpStatus;

/** 에러 코드 공통 인터페이스. 공통 에러는 CommonErrorCode, 도메인 에러는 도메인별 enum이 구현한다. */
public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    // enum 상수 이름이 응답의 code로 사용된다.
    String name();
}
