package com.byarrival.domain.feed.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RssFeedTest {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 3);

    @Test
    void 두_번_연속_실패까지는_활성_상태를_유지한다() {
        RssFeed feed = feed();

        feed.recordFailure(DAY1, MAX_CONSECUTIVE_FAILURES);
        boolean deactivated = feed.recordFailure(DAY1.plusDays(1), MAX_CONSECUTIVE_FAILURES);

        assertThat(deactivated).isFalse();
        assertThat(feed.isActive()).isTrue();
        assertThat(feed.getConsecutiveFailureCount()).isEqualTo(2);
    }

    @Test
    void 세_번_연속_실패하면_비활성화한다() {
        RssFeed feed = feed();

        feed.recordFailure(DAY1, MAX_CONSECUTIVE_FAILURES);
        feed.recordFailure(DAY1.plusDays(1), MAX_CONSECUTIVE_FAILURES);
        boolean deactivated = feed.recordFailure(DAY1.plusDays(2), MAX_CONSECUTIVE_FAILURES);

        assertThat(deactivated).isTrue();
        assertThat(feed.isActive()).isFalse();
        assertThat(feed.getConsecutiveFailureCount()).isEqualTo(3);
    }

    @Test
    void 같은_서비스일에_여러_번_실패해도_한_번만_센다() {
        RssFeed feed = feed();

        feed.recordFailure(DAY1, MAX_CONSECUTIVE_FAILURES);
        feed.recordFailure(DAY1, MAX_CONSECUTIVE_FAILURES);
        boolean deactivated = feed.recordFailure(DAY1, MAX_CONSECUTIVE_FAILURES);

        assertThat(deactivated).isFalse();
        assertThat(feed.isActive()).isTrue();
        assertThat(feed.getConsecutiveFailureCount()).isEqualTo(1);
        assertThat(feed.getLastFailedServiceDate()).isEqualTo(DAY1);
    }

    private RssFeed feed() {
        return RssFeed.create("한국경제", "https://news.example.com/rss.xml", null);
    }
}
