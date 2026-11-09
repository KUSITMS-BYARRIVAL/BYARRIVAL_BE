package com.byarrival.domain.notification.event;

/**
 * 푸시를 보내야 하는 알림이 알림함에 저장됐다. 트랜잭션이 커밋된 뒤 비동기로 푸시를 보낸다.
 */
public record NotificationCreatedEvent(
    Long notificationId
) {
}
