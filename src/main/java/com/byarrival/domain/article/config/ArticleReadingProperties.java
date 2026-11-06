package com.byarrival.domain.article.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예상 읽기시간 계산 기준. 사용자와 무관한 고정 읽기 속도(자/분)
 */
@ConfigurationProperties(prefix = "rss.reading")
public record ArticleReadingProperties(int charsPerMinute) {

    public ArticleReadingProperties {
        if (charsPerMinute <= 0) {
            throw new IllegalArgumentException("rss.reading.chars-per-minute는 0보다 커야 합니다.");
        }
    }
}
