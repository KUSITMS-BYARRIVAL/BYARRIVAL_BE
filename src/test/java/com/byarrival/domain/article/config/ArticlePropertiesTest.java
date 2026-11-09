package com.byarrival.domain.article.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class ArticlePropertiesTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    void 정상_설정은_만들어진다() {
        assertThatCode(() -> crawl(ONE_SECOND, ONE_SECOND, ONE_SECOND, 0.5)).doesNotThrowAnyException();
        assertThatCode(() -> new ArticleSelectionProperties(20, 0.37)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, 50})
    void 한글_비율이_0에서_1_밖이면_거부한다(double ratio) {
        assertThatThrownBy(() -> crawl(ONE_SECOND, ONE_SECOND, ONE_SECOND, ratio))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeout이_1ms보다_짧으면_거부한다() {
        assertThatThrownBy(() -> crawl(Duration.ZERO, ONE_SECOND, ONE_SECOND, 0.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crawl(ONE_SECOND, Duration.ofNanos(1), ONE_SECOND, 0.5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crawl(ONE_SECOND, ONE_SECOND, Duration.ZERO, 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 응답_크기_상한이_없거나_int_범위를_넘으면_거부한다() {
        assertThatThrownBy(() -> crawlWithMaxSize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crawlWithMaxSize(DataSize.ofGigabytes(3))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crawlWithMaxSize(DataSize.ofBytes(0))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, 37})
    void 제목_유사도_기준이_0에서_1_밖이면_거부한다(double threshold) {
        assertThatThrownBy(() -> new ArticleSelectionProperties(20, threshold))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static ArticleCrawlProperties crawlWithMaxSize(DataSize maxSize) {
        return new ArticleCrawlProperties(ONE_SECOND, ONE_SECOND, ONE_SECOND, maxSize, 3, 1, 200, 0.5, "test");
    }

    private static ArticleCrawlProperties crawl(Duration connect, Duration read, Duration request, double ratio) {
        return new ArticleCrawlProperties(connect, read, request, DataSize.ofMegabytes(1), 3, 1, 200, ratio, "test");
    }
}
