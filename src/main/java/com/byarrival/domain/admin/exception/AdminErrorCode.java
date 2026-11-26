package com.byarrival.domain.admin.exception;

import com.byarrival.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다."),
    ADMIN_ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "기사를 찾을 수 없습니다."),
    ADMIN_ARTICLE_UNAVAILABLE(HttpStatus.CONFLICT, "원문이 삭제된 기사는 숨기거나 복구할 수 없습니다."),
    ADMIN_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ADMIN_MEMBER_ALREADY_RESTRICTED(HttpStatus.CONFLICT, "이미 이용 제한 중인 회원입니다. 해제한 뒤 다시 제한해 주세요.");

    private final HttpStatus status;
    private final String message;
}
