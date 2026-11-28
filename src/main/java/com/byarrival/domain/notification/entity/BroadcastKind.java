package com.byarrival.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 알림 유형(NOTI-01). 이벤트는 광고성 정보라 제목 앞에 (광고)를 붙이고 수신 동의자에게만 보낸다.
 */
@Getter
@RequiredArgsConstructor
public enum BroadcastKind {

    NOTICE(NotificationType.SERVICE_NOTICE),
    EVENT(NotificationType.EVENT);

    static final String AD_PREFIX = "(광고) ";

    /** 알림함에 남길 유형 */
    private final NotificationType notificationType;

    /**
     * 사용자에게 보일 제목. 이벤트는 (광고)를 붙인다.
     */
    public String displayTitle(String title) {
        return this == EVENT ? AD_PREFIX + title : title;
    }
}
