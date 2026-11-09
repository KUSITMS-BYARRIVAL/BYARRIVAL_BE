package com.byarrival.domain.notification.entity;

/**
 * 알림의 푸시 발송 결과. SKIPPED는 OS 권한이나 수신 설정이 꺼져 있어 알림함에만 저장한 경우다.
 */
public enum PushStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED,
    NO_DEVICE
}
