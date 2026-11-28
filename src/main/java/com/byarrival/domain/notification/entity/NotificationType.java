package com.byarrival.domain.notification.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 유형. 운영 안내까지 모두 수신 설정 카테고리를 따른다.
 * 자동 알림은 서비스일마다 유형별 1번만 보내고, 그중 스트릭 이탈·인기 기사는 합쳐서 하루 2번까지만 보낸다.
 * 운영 안내는 횟수 제한이 없고, 테스트는 수신 설정·횟수 제한 없이 보낸다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    ROUTE_READY(true, false, NotificationCategory.ACTIVITY),
    ROUTE_INCOMPLETE(true, false, NotificationCategory.ACTIVITY),
    STREAK_MILESTONE(true, false, NotificationCategory.ACTIVITY),
    SMALLTALK(true, false, NotificationCategory.ACTIVITY),
    STREAK_BREAK(true, true, NotificationCategory.ACTIVITY),
    POPULAR_ARTICLE(true, true, NotificationCategory.ACTIVITY),
    SERVICE_NOTICE(false, false, NotificationCategory.ACTIVITY),
    /** 어드민이 보내는 이벤트(광고성) 알림. 광고성 정보 수신 동의자에게만 보낸다 */
    EVENT(false, false, NotificationCategory.ACTIVITY),
    /** 광고성 정보 수신 동의·철회 결과와 2년마다 하는 재확인 안내(정보통신망법). 알림함에는 늘 남는다 */
    MARKETING_CONSENT(false, false, NotificationCategory.ACTIVITY),
    TEST(false, false, null);

    /** 서비스일마다 이 유형은 1번만 보낸다(DB 유니크 인덱스와 같다) */
    private final boolean oncePerDay;
    /** 하루 발송 횟수 제한에 들어간다 */
    private final boolean dailyCapped;
    private final NotificationCategory category;

    public static List<NotificationType> dailyCappedTypes() {
        return Arrays.stream(values()).filter(NotificationType::isDailyCapped).toList();
    }
}
