package com.byarrival.domain.batch.scheduler;

import com.byarrival.domain.batch.service.DailyBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일일 배치를 매일 23:45(KST)에 시작한다.
 */
@Component
@RequiredArgsConstructor
public class DailyBatchScheduler {

    private final DailyBatchService dailyBatchService;

    @Scheduled(cron = "0 45 23 * * *")
    public void run() {
        dailyBatchService.run();
    }
}
