package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.feed.dto.response.FeedResponse;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class FeedServiceTest {

    private static final String FEED_URL = "https://news.example.com/rss/economy.xml";

    private RssFeedRepository rssFeedRepository;
    private FeedService feedService;

    @BeforeEach
    void setUp() {
        rssFeedRepository = mock(RssFeedRepository.class);
        FeedUrlValidator validator = new FeedUrlValidator(host -> new InetAddress[] {InetAddress.getByName("203.0.113.10")});
        feedService = new FeedService(rssFeedRepository, validator);
    }

    @Test
    void Feed를_등록하면_활성_상태로_저장된다() {
        given(rssFeedRepository.existsByFeedUrl(FEED_URL)).willReturn(false);
        given(rssFeedRepository.saveAndFlush(any(RssFeed.class))).willAnswer(invocation -> {
            RssFeed feed = invocation.getArgument(0);
            ReflectionTestUtils.setField(feed, "id", 1L);
            return feed;
        });

        FeedResponse response = feedService.register(" 한국경제 ", FEED_URL, " 경제 ");

        assertThat(response.feedId()).isEqualTo(1L);
        assertThat(response.pressName()).isEqualTo("한국경제");
        assertThat(response.feedUrl()).isEqualTo(FEED_URL);
        assertThat(response.rssCategory()).isEqualTo("경제");
        assertThat(response.active()).isTrue();
    }

    @Test
    void 카테고리_없이도_등록할_수_있다() {
        given(rssFeedRepository.saveAndFlush(any(RssFeed.class))).willAnswer(invocation -> invocation.getArgument(0));

        FeedResponse response = feedService.register("한국경제", FEED_URL, " ");

        assertThat(response.rssCategory()).isNull();
    }

    @Test
    void 이미_등록된_URL이면_등록하지_않는다() {
        given(rssFeedRepository.existsByFeedUrl(FEED_URL)).willReturn(true);

        assertThatThrownBy(() -> feedService.register("한국경제", FEED_URL, "경제"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(FeedErrorCode.DUPLICATE_FEED_URL);
        verify(rssFeedRepository, never()).saveAndFlush(any());
    }

    @Test
    void 중복_확인_후_다른_요청이_먼저_저장하면_중복_URL로_응답한다() {
        given(rssFeedRepository.existsByFeedUrl(FEED_URL)).willReturn(false);
        given(rssFeedRepository.saveAndFlush(any(RssFeed.class)))
            .willThrow(integrityViolation("23505", FeedService.FEED_URL_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> feedService.register("한국경제", FEED_URL, "경제"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(FeedErrorCode.DUPLICATE_FEED_URL);
    }

    @Test
    void feed_url_중복이_아닌_무결성_오류는_그대로_던진다() {
        DataIntegrityViolationException notNull = integrityViolation("23502", null);
        DataIntegrityViolationException primaryKey = integrityViolation("23505", "rss_feed_pkey");
        given(rssFeedRepository.saveAndFlush(any(RssFeed.class))).willThrow(notNull, primaryKey);

        assertThatThrownBy(() -> feedService.register("한국경제", FEED_URL, "경제")).isSameAs(notNull);
        assertThatThrownBy(() -> feedService.register("한국경제", FEED_URL, "경제")).isSameAs(primaryKey);
    }

    @Test
    void 사용할_수_없는_URL이면_등록하지_않는다() {
        assertThatThrownBy(() -> feedService.register("한국경제", "ftp://news.example.com/rss", "경제"))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(FeedErrorCode.INVALID_FEED_URL);
        verify(rssFeedRepository, never()).saveAndFlush(any());
    }

    @Test
    void 언론사명이_비어있거나_50자를_넘으면_등록하지_않는다() {
        assertInvalidInput(() -> feedService.register(" ", FEED_URL, "경제"));
        assertInvalidInput(() -> feedService.register(null, FEED_URL, "경제"));
        assertInvalidInput(() -> feedService.register("가".repeat(51), FEED_URL, "경제"));
    }

    @Test
    void 카테고리가_50자를_넘으면_등록하지_않는다() {
        assertInvalidInput(() -> feedService.register("한국경제", FEED_URL, "가".repeat(51)));
    }

    @Test
    void 활성_Feed만_수집_대상으로_조회한다() {
        RssFeed active = RssFeed.create("한국경제", FEED_URL, "경제");
        given(rssFeedRepository.findAllByActiveTrue()).willReturn(List.of(active));

        assertThat(feedService.getActiveFeeds()).containsExactly(active);
    }

    @Test
    void Feed를_비활성_처리한다() {
        RssFeed feed = RssFeed.create("한국경제", FEED_URL, "경제");
        given(rssFeedRepository.findById(1L)).willReturn(Optional.of(feed));

        feedService.deactivate(1L);

        assertThat(feed.isActive()).isFalse();
    }

    @Test
    void 없는_Feed를_비활성_처리하면_예외가_발생한다() {
        given(rssFeedRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> feedService.deactivate(1L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
    }

    private DataIntegrityViolationException integrityViolation(String sqlState, String constraintName) {
        SQLException sqlException = new SQLException("constraint violation", sqlState);
        return new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("could not execute statement", sqlException, constraintName));
    }

    private void assertInvalidInput(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
        verify(rssFeedRepository, never()).saveAndFlush(any());
    }
}
