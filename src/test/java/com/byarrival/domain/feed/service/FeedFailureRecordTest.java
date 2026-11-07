package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class FeedFailureRecordTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 3);

    @Autowired
    private FeedService feedService;

    @Autowired
    private RssFeedRepository rssFeedRepository;

    @AfterEach
    void tearDown() {
        rssFeedRepository.deleteAll();
    }

    @Test
    void 서비스일이_다른_실패가_세_번_쌓이면_비활성화된다() {
        Long feedId = saveFeed().getId();

        assertThat(feedService.recordFailures(List.of(feedId), DAY1)).isEmpty();
        assertThat(feedService.recordFailures(List.of(feedId), DAY1)).isEmpty();
        assertThat(feedService.recordFailures(List.of(feedId), DAY1.plusDays(1))).isEmpty();
        assertThat(feedService.recordFailures(List.of(feedId), DAY1.plusDays(2))).containsExactly(feedId);

        RssFeed feed = rssFeedRepository.findById(feedId).orElseThrow();
        assertThat(feed.isActive()).isFalse();
        assertThat(feed.getConsecutiveFailureCount()).isEqualTo(3);
        assertThat(feedService.getActiveFeeds()).isEmpty();
    }

    @Test
    void 수집에_성공하면_연속_실패_횟수를_초기화한다() {
        Long feedId = saveFeed().getId();
        feedService.recordFailures(List.of(feedId), DAY1);
        feedService.recordFailures(List.of(feedId), DAY1.plusDays(1));

        feedService.markFetched(List.of(feedId), LocalDateTime.of(2026, 11, 4, 23, 46));

        RssFeed feed = rssFeedRepository.findById(feedId).orElseThrow();
        assertThat(feed.getConsecutiveFailureCount()).isZero();
        assertThat(feed.getLastFailedServiceDate()).isNull();
        assertThat(feed.isActive()).isTrue();
    }

    private RssFeed saveFeed() {
        return rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/rss.xml", null));
    }
}
