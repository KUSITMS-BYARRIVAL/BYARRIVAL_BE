package com.byarrival.domain.feed.service;

import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.entity.RssFeed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 활성 Feed를 하나씩 호출해 RSS 원문을 모은다. 한 Feed가 실패해도 나머지는 계속 수집한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedCollectService {

    private final FeedService feedService;
    private final RssFeedFetcher rssFeedFetcher;
    private final Clock clock;

    // 외부 호출이 트랜잭션을 붙잡지 않도록 트랜잭션을 걸지 않는다.
    public List<FetchedFeed> collect() {
        List<RssFeed> feeds = feedService.getActiveFeeds();
        List<FetchedFeed> fetchedFeeds = new ArrayList<>();
        for (RssFeed feed : feeds) {
            try {
                fetchedFeeds.add(FetchedFeed.of(feed.getId(), rssFeedFetcher.fetch(feed.getFeedUrl())));
            } catch (RuntimeException e) {
                log.warn("[FeedCollect] feedId={} 수집 실패 - {}", feed.getId(), e.getMessage());
            }
        }

        feedService.markFetched(fetchedFeeds.stream().map(FetchedFeed::feedId).toList(), LocalDateTime.now(clock));
        log.info("[FeedCollect] 활성 Feed {}개 중 {}개 수집", feeds.size(), fetchedFeeds.size());
        return fetchedFeeds;
    }
}
