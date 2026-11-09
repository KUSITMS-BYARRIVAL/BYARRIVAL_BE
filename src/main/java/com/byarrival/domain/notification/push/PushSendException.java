package com.byarrival.domain.notification.push;

/**
 * 푸시 서버 호출 자체가 실패한 경우. 기기별 실패는 PushResult로 돌려준다.
 */
public class PushSendException extends RuntimeException {

    public PushSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
