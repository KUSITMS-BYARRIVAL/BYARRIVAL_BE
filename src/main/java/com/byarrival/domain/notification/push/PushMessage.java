package com.byarrival.domain.notification.push;

import java.util.Map;

/**
 * 기기로 보낼 푸시 한 건. data의 값은 FCM 제약에 맞춰 모두 문자열이다.
 */
public record PushMessage(
    String title,
    String body,
    Map<String, String> data
) {
}
