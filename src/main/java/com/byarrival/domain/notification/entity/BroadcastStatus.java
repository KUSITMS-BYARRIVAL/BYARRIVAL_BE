package com.byarrival.domain.notification.entity;

/**
 * 어드민 알림 발송 상태. SCHEDULED는 즉시 발송도 발송을 시작하기 전까지의 상태다.
 */
public enum BroadcastStatus {
    SCHEDULED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELED
}
