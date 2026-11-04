package com.byarrival.domain.feed.service;

import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.entity.RssFeed;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 활성 Feed를 하나씩 호출해 RSS 원문을 모은다. 한 Feed가 실패해도 나머지는 계속 수집한다.
 * 실패한 Feed는 연속 실패를 세고, 기준 횟수에 닿으면 비활성화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedCollectService {

    private final FeedService feedService;
    private final RssFeedFetcher rssFeedFetcher;
    private final Clock clock;

    // 외부 호출이 트랜잭션을 붙잡지 않도록 트랜잭션을 걸지 않는다.
    public List<FetchedFeed> collect(LocalDate serviceDate) {
        List<RssFeed> feeds = feedService.getActiveFeeds();
        List<FetchedFeed> fetchedFeeds = new ArrayList<>();
        List<Long> failedFeedIds = new ArrayList<>();
        for (RssFeed feed : feeds) {
            try {
                fetchedFeeds.add(FetchedFeed.of(feed.getId(), rssFeedFetcher.fetch(feed.getFeedUrl())));
            } catch (RuntimeException e) {
                failedFeedIds.add(feed.getId());
                log.warn("[FeedCollect] feedId={} 수집 실패 - {}", feed.getId(), e.getMessage());
            }
        }

        feedService.markFetched(fetchedFeeds.stream().map(FetchedFeed::feedId).toList(), LocalDateTime.now(clock));
        List<Long> deactivatedFeedIds = feedService.recordFailures(failedFeedIds, serviceDate);
        if (!deactivatedFeedIds.isEmpty()) {
            log.warn("[FeedCollect] 연속 실패로 비활성화한 feedIds={}", deactivatedFeedIds);
        }
        log.info("[FeedCollect] 활성 Feed {}개 중 {}개 수집", feeds.size(), fetchedFeeds.size());
        return fetchedFeeds;
    }
}
