package com.byarrival.domain.event.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.event.service.UsageEventCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간(6개월)이 지난 이용 이벤트 로그를 매일 04:40(KST)에 지운다. 블루/그린 전환 중 두 번 실행돼도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageEventCleanupScheduler {

    private final UsageEventCleanupService usageEventCleanupService;

    @Scheduled(cron = "0 40 4 * * *", zone = ClockConfig.KST)
    public void deleteExpired() {
        int deleted = usageEventCleanupService.deleteExpired();
        log.info("보관 기간이 지난 이용 이벤트 로그 삭제: {}건", deleted);
    }
}
