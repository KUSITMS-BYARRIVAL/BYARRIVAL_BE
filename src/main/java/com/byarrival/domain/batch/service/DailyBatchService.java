package com.byarrival.domain.batch.service;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.service.FeedCollectService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 매일 23:45 일일 배치. 다음 서비스일에 노출할 기사를 수집한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBatchService {

    private final BatchRunService batchRunService;
    private final FeedCollectService feedCollectService;
    private final Clock clock;

    public void run() {
        // 23:45에 수집한 기사는 다음 04:00부터 시작하는 서비스일에 노출된다
        LocalDate serviceDate = ServiceDay.today(clock).plusDays(1);
        Optional<Long> batchRunId = batchRunService.start(serviceDate);
        if (batchRunId.isEmpty()) {
            log.info("[DailyBatch] serviceDate={} 배치가 이미 실행 중이거나 완료되어 건너뜀", serviceDate);
            return;
        }

        try {
            List<FetchedFeed> fetchedFeeds = feedCollectService.collect();
            batchRunService.complete(batchRunId.get());
            log.info("[DailyBatch] serviceDate={} 완료 - 수집 Feed {}개", serviceDate, fetchedFeeds.size());
        } catch (RuntimeException e) {
            log.error("[DailyBatch] serviceDate={} 실패 - {}", serviceDate, e.getMessage(), e);
            batchRunService.fail(batchRunId.get(), e.getMessage());
        }
    }
}
