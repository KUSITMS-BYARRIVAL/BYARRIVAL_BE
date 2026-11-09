package com.byarrival.domain.notification.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간(30일)이 지난 알림을 매일 04:30(KST)에 지운다. 블루/그린 전환 중 두 번 실행돼도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 30 4 * * *", zone = ClockConfig.KST)
    public void deleteExpired() {
        int deleted = notificationService.deleteExpired();
        log.info("보관 기간이 지난 알림 삭제: {}건", deleted);
    }
}
