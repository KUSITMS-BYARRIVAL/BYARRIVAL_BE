package com.byarrival.domain.notification.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 유형. 운영 안내까지 모두 수신 설정 카테고리를 따른다.
 * 행동 유도 알림만 하루 발송 횟수와 유형별 하루 1회 제한을 받는다. 테스트는 수신 설정·횟수 제한 없이 보낸다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    ROUTE_READY(true, NotificationCategory.ACTIVITY),
    ROUTE_INCOMPLETE(true, NotificationCategory.ACTIVITY),
    STREAK_MILESTONE(true, NotificationCategory.ACTIVITY),
    SMALLTALK(true, NotificationCategory.ACTIVITY),
    STREAK_BREAK(true, NotificationCategory.ACTIVITY),
    POPULAR_ARTICLE(true, NotificationCategory.ACTIVITY),
    SERVICE_NOTICE(false, NotificationCategory.ACTIVITY),
    TEST(false, null);

    private final boolean action;
    private final NotificationCategory category;

    public static List<NotificationType> actionTypes() {
        return Arrays.stream(values()).filter(NotificationType::isAction).toList();
    }
}
