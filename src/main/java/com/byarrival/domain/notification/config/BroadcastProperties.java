package com.byarrival.domain.notification.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 어드민 알림 발송 설정.
 *
 * @param enabled     false면 예약 발송 작업을 돌리지 않는다(테스트)
 * @param testUserIds 테스트 발송(NOTI-02)을 받을 앱 회원 ID. 운영자 기기에 로그인한 회원을 적는다
 */
@ConfigurationProperties(prefix = "notification.broadcast")
public record BroadcastProperties(boolean enabled, List<Long> testUserIds) {

    public BroadcastProperties {
        testUserIds = testUserIds == null ? List.of() : List.copyOf(testUserIds);
    }
}
