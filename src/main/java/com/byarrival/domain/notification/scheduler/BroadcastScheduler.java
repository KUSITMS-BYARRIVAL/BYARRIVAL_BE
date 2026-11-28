package com.byarrival.domain.notification.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.notification.service.BroadcastDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 예약 시각이 된 어드민 알림을 보낸다. 즉시 발송이 서버 재시작으로 끊겨도 여기서 이어 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.broadcast", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class BroadcastScheduler {

    private final BroadcastDispatcher dispatcher;

    @Scheduled(cron = "0 * * * * *", zone = ClockConfig.KST)
    public void dispatchDue() {
        try {
            dispatcher.dispatchDue();
        } catch (RuntimeException e) {
            log.error("[Broadcast] 예약 발송 작업 실패", e);
        }
    }
}
