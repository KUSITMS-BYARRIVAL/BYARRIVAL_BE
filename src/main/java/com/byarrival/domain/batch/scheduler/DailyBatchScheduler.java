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

    static final String RUN_CRON = "0 45 23 * * *";
    // 23:45 배치가 실패하면 마감(23:59) 전에 두 번 다시 시도한다(RSS-02). 2026-11-11 운영 배치가 4분 21초 걸린 기준이다
    static final String RETRY_CRON = "0 52,56 23 * * *";

    @Scheduled(cron = RUN_CRON, zone = ClockConfig.KST)
    public void run() {
        execute();
    }

    /**
     * 같은 서비스일 배치를 다시 실행한다. 이미 성공했거나 아직 실행 중이면 배치가 시작 단계에서 건너뛴다.
     * 실패로 끝난 배치만 다시 돌며, 이미 저장·가공한 기사는 건너뛰고 남은 것만 처리한다.
     */
    @Scheduled(cron = RETRY_CRON, zone = ClockConfig.KST)
    public void retry() {
        log.info("[DailyBatch] 재시도 시각 - 실패한 배치가 있으면 다시 실행");
        execute();
    }

    private void execute() {
        try {
            dailyBatchService.run();
        } catch (RuntimeException e) {
            // 배치 시작 기록이나 실패 기록 자체가 실패한 경우. 스케줄러 기본 처리는 로그만 남기므로 직접 알린다
            log.error("[DailyBatch] 비정상 종료", e);
            batchMonitor.crashed(e);
        }
    }
}
