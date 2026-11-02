package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FeedCollectServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 23, 45);

    private FeedService feedService;
    private RssFeedFetcher rssFeedFetcher;
    private FeedCollectService feedCollectService;

    @BeforeEach
    void setUp() {
        feedService = mock(FeedService.class);
        rssFeedFetcher = mock(RssFeedFetcher.class);
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        feedCollectService = new FeedCollectService(feedService, rssFeedFetcher, clock);
    }

    @Test
    void 활성_Feed를_모두_호출해_원문을_모은다() {
        RssFeed economy = feed(1L, "https://news.example.com/economy.xml");
        RssFeed society = feed(2L, "https://news.example.com/society.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(economy, society));
        given(rssFeedFetcher.fetch(economy.getFeedUrl())).willReturn(new byte[] {1});
        given(rssFeedFetcher.fetch(society.getFeedUrl())).willReturn(new byte[] {2});

        List<FetchedFeed> fetchedFeeds = feedCollectService.collect();

        assertThat(fetchedFeeds).extracting(FetchedFeed::feedId).containsExactly(1L, 2L);
        verify(feedService).markFetched(List.of(1L, 2L), NOW);
    }

    @Test
    void 한_Feed가_실패해도_나머지_Feed는_수집한다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        RssFeed economy = feed(2L, "https://news.example.com/economy.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken, economy));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));
        given(rssFeedFetcher.fetch(economy.getFeedUrl())).willReturn(new byte[] {1});

        List<FetchedFeed> fetchedFeeds = feedCollectService.collect();

        assertThat(fetchedFeeds).extracting(FetchedFeed::feedId).containsExactly(2L);
        verify(feedService).markFetched(List.of(2L), NOW);
    }

    @Test
    void 수집에_실패한_Feed는_비활성으로_바꾸지_않는다() {
        RssFeed broken = feed(1L, "https://broken.example.com/rss.xml");
        given(feedService.getActiveFeeds()).willReturn(List.of(broken));
        given(rssFeedFetcher.fetch(broken.getFeedUrl()))
            .willThrow(new BusinessException(FeedErrorCode.FEED_FETCH_FAILED));

        List<FetchedFeed> fetchedFeeds = feedCollectService.collect();

        assertThat(fetchedFeeds).isEmpty();
        assertThat(broken.isActive()).isTrue();
        verify(feedService).markFetched(List.of(), NOW);
    }

    private RssFeed feed(Long id, String feedUrl) {
        RssFeed feed = RssFeed.create("한국경제", feedUrl, null);
        ReflectionTestUtils.setField(feed, "id", id);
        return feed;
    }
}
