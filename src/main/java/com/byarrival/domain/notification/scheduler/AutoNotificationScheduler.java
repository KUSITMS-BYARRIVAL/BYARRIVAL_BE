package com.byarrival.domain.notification.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.notification.auto.CommuteNotificationTrigger;
import com.byarrival.domain.notification.auto.RouteIncompleteTrigger;
import com.byarrival.domain.notification.auto.SmalltalkNotificationTrigger;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시각 기준 자동 알림(DCK-34). 출근 시각 기준 알림과 미완주 리마인드는 1분마다, 스몰토크는 매일 16:00에 보낸다.
 * 블루/그린 전환 중 두 서버에서 함께 돌아도 유형별 하루 1번 제약으로 한 번만 저장된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.auto", name = "enabled", havingValue = "true")
public class AutoNotificationScheduler {

    private final CommuteNotificationTrigger commuteTrigger;
    private final RouteIncompleteTrigger routeIncompleteTrigger;
    private final SmalltalkNotificationTrigger smalltalkTrigger;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = ClockConfig.KST)
    public void everyMinute() {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            commuteTrigger.run(now);
        } catch (RuntimeException e) {
            log.error("[AutoNotification] 출근 시각 알림 작업 실패", e);
        }
        try {
            routeIncompleteTrigger.run(now);
        } catch (RuntimeException e) {
            log.error("[AutoNotification] 미완주 리마인드 작업 실패", e);
        }
    }

    @Scheduled(cron = "0 0 13 * * *", zone = ClockConfig.KST)
    public void smalltalk() {
        try {
            smalltalkTrigger.run(ServiceDay.today(clock));
        } catch (RuntimeException e) {
            log.error("[AutoNotification] 스몰토크 알림 작업 실패", e);
        }
    }
}
