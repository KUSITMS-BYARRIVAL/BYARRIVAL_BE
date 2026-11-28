package com.byarrival.domain.admin.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.admin.service.AdminMemberAccessLogCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간(2년)이 지난 회원 열람 기록을 매일 04:50(KST)에 지운다. 블루/그린 전환 중 두 번 실행돼도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminMemberAccessLogCleanupScheduler {

    private final AdminMemberAccessLogCleanupService cleanupService;

    @Scheduled(cron = "0 50 4 * * *", zone = ClockConfig.KST)
    public void deleteExpired() {
        int deleted = cleanupService.deleteExpired();
        log.info("보관 기간이 지난 회원 열람 기록 삭제: {}건", deleted);
    }
}
