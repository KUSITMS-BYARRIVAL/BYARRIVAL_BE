package com.byarrival.domain.notification.entity;

/**
 * 사용자가 수신 여부를 켜고 끄는 단위. 지금은 전체 알림(행동 유도·운영 안내)을 한 번에 켜고 끈다.
 * 설정 화면이 나뉘면(예: 아침/저녁) 값을 추가하고 NotificationType의 연결만 바꾼다.
 */
public enum NotificationCategory {
    ACTIVITY
}
