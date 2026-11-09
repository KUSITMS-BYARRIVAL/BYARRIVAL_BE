package com.byarrival.domain.batch.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.batch.service.BatchMonitor;
import com.byarrival.domain.batch.service.DailyBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일일 배치를 매일 23:45(KST)에 시작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyBatchScheduler {

    private final DailyBatchService dailyBatchService;
    private final BatchMonitor batchMonitor;

    @Scheduled(cron = "0 45 23 * * *", zone = ClockConfig.KST)
    public void run() {
        try {
            dailyBatchService.run();
        } catch (RuntimeException e) {
            // 배치 시작 기록이나 실패 기록 자체가 실패한 경우. 스케줄러 기본 처리는 로그만 남기므로 직접 알린다
            log.error("[DailyBatch] 비정상 종료", e);
            batchMonitor.crashed(e);
        }
    }
}
