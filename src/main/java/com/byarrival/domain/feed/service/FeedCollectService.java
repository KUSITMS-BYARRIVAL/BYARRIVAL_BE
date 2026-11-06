package com.byarrival.domain.feed.service;

import com.byarrival.domain.feed.dto.FeedCollectResult;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
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
 * 활성 Feed를 하나씩 호출해 RSS를 파싱한다. 한 Feed가 실패해도 나머지는 계속 수집한다.
 * 호출이나 파싱에 실패한 Feed는 연속 실패를 세고, 기준 횟수에 닿으면 비활성화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedCollectService {

    private final FeedService feedService;
    private final RssFeedFetcher rssFeedFetcher;
    private final RssParser rssParser;
    private final Clock clock;

    // 외부 호출이 트랜잭션을 붙잡지 않도록 트랜잭션을 걸지 않는다.
    public FeedCollectResult collect(LocalDate serviceDate) {
        List<RssFeed> feeds = feedService.getActiveFeeds();
        List<FetchedFeed> fetchedFeeds = new ArrayList<>();
        List<Long> failedFeedIds = new ArrayList<>();
        for (RssFeed feed : feeds) {
            try {
                // 파싱 실패도 수집 실패로 센다. 성공 처리(markFetched)는 실패 횟수를 초기화하므로 파싱까지 끝난 Feed만 넘긴다.
                List<RssItem> items = rssParser.parse(feed.getId(), rssFeedFetcher.fetch(feed.getFeedUrl()));
                fetchedFeeds.add(FetchedFeed.of(feed, items));
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
        return FeedCollectResult.of(fetchedFeeds, failedFeedIds);
    }
}
