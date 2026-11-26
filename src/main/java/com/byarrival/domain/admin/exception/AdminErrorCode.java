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
    ADMIN_MEMBER_ALREADY_RESTRICTED(HttpStatus.CONFLICT, "이미 이용 제한 중인 회원입니다. 해제한 뒤 다시 제한해 주세요."),
    ADMIN_EDITOR_PICK_NOT_FOUND(HttpStatus.NOT_FOUND, "에디터 픽을 찾을 수 없습니다."),
    ADMIN_EDITOR_PICK_PAST(HttpStatus.CONFLICT, "지난 노출일의 에디터 픽은 바꿀 수 없습니다."),
    ADMIN_EDITOR_PICK_ARTICLE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "오늘 노출 중이거나 노출일 당일 기사만 연결할 수 있습니다."),
    ADMIN_EDITOR_PICK_ARTICLE_TAKEN(HttpStatus.CONFLICT, "이미 에디터 픽에 연결된 기사입니다."),
    ADMIN_EDITOR_PICK_ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "그 노출일의 에디터 픽을 빠짐없이 한 번씩 보내야 합니다.");

    private final HttpStatus status;
    private final String message;
}
