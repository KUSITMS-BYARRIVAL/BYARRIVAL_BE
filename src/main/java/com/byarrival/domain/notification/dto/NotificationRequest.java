package com.byarrival.domain.notification.dto;

import com.byarrival.domain.notification.entity.NotificationType;
import java.util.Map;

/**
 * 다른 기능이 알림을 보낼 때 넘기는 내용. params는 앱이 화면을 열 때 쓸 값이다(예: routeId).
 */
public record NotificationRequest(
    Long userId,
    NotificationType type,
    String title,
    String body,
    Map<String, String> params
) {

    public static NotificationRequest of(Long userId, NotificationType type, String title, String body) {
        return new NotificationRequest(userId, type, title, body, Map.of());
    }
}
