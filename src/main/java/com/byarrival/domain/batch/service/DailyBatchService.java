package com.byarrival.domain.batch.service;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.article.service.ArticleCollectService;
import com.byarrival.domain.feed.dto.FeedCollectResult;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.service.FeedCollectService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 매일 23:45 일일 배치. 다음 서비스일에 노출할 기사를 수집하고, 카테고리별로 고른 기사의 원문을 크롤링해 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBatchService {

    private final BatchRunService batchRunService;
    private final FeedCollectService feedCollectService;
    private final ArticleCollectService articleCollectService;
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
            FeedCollectResult collectResult = feedCollectService.collect(serviceDate);
            if (collectResult.allFailed()) {
                // 다시 실행할 수 있도록 실패로 남긴다. 실패 횟수는 서비스일당 1회만 세므로 재실행해도 늘지 않는다.
                batchRunService.fail(batchRunId.get(), "모든 Feed 수집 실패 feedIds=" + collectResult.failedFeedIds());
                return;
            }
            List<FetchedFeed> fetchedFeeds = collectResult.fetchedFeeds();
            List<Long> saveFailedFeedIds = saveArticles(fetchedFeeds, serviceDate);
            if (!saveFailedFeedIds.isEmpty()) {
                // 다시 실행할 수 있도록 실패로 남긴다. 이미 저장된 기사는 재실행 때 건너뛴다.
                batchRunService.fail(batchRunId.get(), "기사 저장 실패 feedIds=" + saveFailedFeedIds);
                return;
            }
            batchRunService.complete(batchRunId.get());
            log.info("[DailyBatch] serviceDate={} 완료 - 수집 Feed {}개", serviceDate, fetchedFeeds.size());
        } catch (RuntimeException e) {
            log.error("[DailyBatch] serviceDate={} 실패 - {}", serviceDate, e.getMessage(), e);
            batchRunService.fail(batchRunId.get(), e.getMessage());
        }
    }

    // 한 Feed의 저장이 실패해도 나머지 Feed는 저장한다
    private List<Long> saveArticles(List<FetchedFeed> fetchedFeeds, LocalDate serviceDate) {
        List<Long> failedFeedIds = new ArrayList<>();
        int saved = 0;
        for (FetchedFeed feed : fetchedFeeds) {
            try {
                saved += articleCollectService.collect(feed, serviceDate);
            } catch (RuntimeException e) {
                failedFeedIds.add(feed.feedId());
                log.error("[DailyBatch] serviceDate={} feedId={} 기사 저장 실패 - {}",
                    serviceDate, feed.feedId(), e.getMessage(), e);
            }
        }
        log.info("[DailyBatch] serviceDate={} 기사 {}건 저장", serviceDate, saved);
        return failedFeedIds;
    }
}
