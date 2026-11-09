package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.feed.dto.FeedCollectResult;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FeedCollectServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 23, 45);
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 3);
    private static final byte[] ECONOMY_XML = {1};
    private static final byte[] SOCIETY_XML = {2};

    private FeedService feedService;
    private RssFeedFetcher rssFeedFetcher;
    private RssParser rssParser;
    private SimpleMeterRegistry meterRegistry;
    private BatchAlertNotifier batchAlertNotifier;
    private FeedCollectService feedCollectService;

    @BeforeEach
    void setUp() {
        feedService = mock(FeedService.class);
        rssFeedFetcher = mock(RssFeedFetcher.class);
        rssParser = mock(RssParser.class);
        meterRegistry = new SimpleMeterRegistry();
        batchAlertNotifier = mock(BatchAlertNotifier.class);
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        feedCollectService = new FeedCollectService(feedService, rssFeedFetcher, rssParser, meterRegistry,
            batchAlertNotifier, clock);
    }

    @Test
    void 활성_Feed를_모두_호출해_파싱한다() {
        RssFeed economy = feed(1L, "https://news.example.com/economy.xml");
        RssFeed society = feed(2L, "https://news.example.com/society.xml");
        RssItem item = item();
        given(feedService.getActiveFeeds()).willReturn(List.of(economy, society));
        given(rssFeedFetcher.fetch(economy.getFeedUrl())).willReturn(ECONOMY_XML);
        given(rssFeedFetcher.fetch(society.getFeedUrl())).willReturn(SOCIETY_XML);
        given(rssParser.parse(1L, ECONOMY_XML)).willReturn(List.of(item));
        given(rssParser.parse(2L, SOCIETY_XML)).willReturn(List.of());

        FeedCollectResult result = feedCollectService.collect(SERVICE_DATE);

        assertThat(result.fetchedFeeds()).containsExactly(
            new FetchedFeed(1L, "한국경제", "경제", List.of(item)),
            new FetchedFeed(2L, "한국경제", "경제", List.of()));
        verify(feedService).markFetched(List.of(1L, 2L), NOW);
        verify(feedService).recordFailures(List.of(), SERVICE_DATE);
    }

    @Test
    void 한_Feed가_실패해도_나머지_Feed는_수집한다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        RssFeed economy = feed(2L, "https://news.example.com/economy.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken, economy));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));
        given(rssFeedFetcher.fetch(economy.getFeedUrl())).willReturn(ECONOMY_XML);
        given(rssParser.parse(2L, ECONOMY_XML)).willReturn(List.of());

        FeedCollectResult result = feedCollectService.collect(SERVICE_DATE);

        assertThat(result.fetchedFeeds()).extracting(FetchedFeed::feedId).containsExactly(2L);
        assertThat(result.failedFeedIds()).containsExactly(1L);
        assertThat(result.allFailed()).isFalse();
        verify(feedService).markFetched(List.of(2L), NOW);
    }

    @Test
    void 수집에_실패한_Feed는_서비스일과_함께_실패로_기록한다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));

        FeedCollectResult result = feedCollectService.collect(SERVICE_DATE);

        assertThat(result.fetchedFeeds()).isEmpty();
        assertThat(result.allFailed()).isTrue();
        verify(feedService).markFetched(List.of(), NOW);
        verify(feedService).recordFailures(List.of(1L), SERVICE_DATE);
    }

    @Test
    void 파싱에_실패한_Feed는_성공으로_처리하지_않고_실패로_기록한다() {
        RssFeed errorPage = feed(1L, "https://news.example.com/error.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(errorPage));
        given(rssFeedFetcher.fetch(errorPage.getFeedUrl())).willReturn(ECONOMY_XML);
        given(rssParser.parse(1L, ECONOMY_XML)).willThrow(new BusinessException(FeedErrorCode.FEED_PARSE_FAILED));

        FeedCollectResult result = feedCollectService.collect(SERVICE_DATE);

        assertThat(result.fetchedFeeds()).isEmpty();
        assertThat(result.allFailed()).isTrue();
        verify(feedService).markFetched(List.of(), NOW);
        verify(feedService).recordFailures(List.of(1L), SERVICE_DATE);
    }

    @Test
    void 수집에_실패한_Feed_수를_메트릭으로_남긴다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));
        given(feedService.recordFailures(List.of(1L), SERVICE_DATE)).willReturn(List.of());

        feedCollectService.collect(SERVICE_DATE);

        assertThat(meterRegistry.counter(FeedCollectService.FETCH_FAILURE_METRIC).count()).isEqualTo(1.0);
        verify(batchAlertNotifier, never()).send(any());
    }

    @Test
    void 연속_실패로_비활성화한_Feed는_배치_채널에_알린다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));
        given(feedService.recordFailures(List.of(1L), SERVICE_DATE)).willReturn(List.of(1L));

        feedCollectService.collect(SERVICE_DATE);

        assertThat(meterRegistry.counter(FeedCollectService.DEACTIVATED_METRIC).count()).isEqualTo(1.0);
        verify(batchAlertNotifier).send(contains("feedIds=[1]"));
    }

    @Test
    void 비활성화_알림이_실패해도_수집_결과를_돌려준다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));
        given(feedService.recordFailures(List.of(1L), SERVICE_DATE)).willReturn(List.of(1L));
        willThrow(new IllegalStateException("알림 실패")).given(batchAlertNotifier).send(any());

        FeedCollectResult result = feedCollectService.collect(SERVICE_DATE);

        assertThat(result.failedFeedIds()).containsExactly(1L);
    }

    private RssItem item() {
        return new RssItem("guid-1", "https://news.example.com/articles/1", "제목", "본문",
            LocalDateTime.of(2026, 11, 2, 20, 0), List.of(), null);
    }

    private RssFeed feed(Long id, String feedUrl) {
        RssFeed feed = RssFeed.create("한국경제", feedUrl, "경제");
        ReflectionTestUtils.setField(feed, "id", id);
        return feed;
    }
}
