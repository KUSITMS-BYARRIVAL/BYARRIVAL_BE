package com.byarrival.domain.notification.dto.response;

import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 알림함 항목. 앱은 type을 보고 이동할 화면을 정하고, params에서 필요한 id를 꺼낸다.
 */
public record NotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String body,
    Map<String, String> params,
    boolean read,
    LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
            notification.getBody(), notification.getParams(), notification.isRead(), notification.getCreatedAt());
    }
}
