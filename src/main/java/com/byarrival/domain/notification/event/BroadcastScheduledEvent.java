package com.byarrival.domain.notification.event;

/**
 * 어드민 알림을 만들었다. 즉시 발송이면 커밋 뒤 바로 보내기 시작한다.
 */
public record BroadcastScheduledEvent(Long broadcastId) {
}
