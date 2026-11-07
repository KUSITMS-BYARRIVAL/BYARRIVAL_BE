package com.byarrival.domain.article.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 기사 원문 크롤링 제한값
 *
 * @param concurrency      동시에 크롤링하는 기사 수
 * @param minContentLength 정제한 본문이 이보다 짧으면 본문을 찾지 못한 것으로 본다
 * @param minHangulRatio   제목·본문 글자 중 한글 비율이 이보다 낮으면 외국어 기사로 보고 뺀다(0~1)
 * @param userAgent        크롤링 요청의 User-Agent
 */
@ConfigurationProperties(prefix = "rss.crawl")
public record ArticleCrawlProperties(
    Duration connectTimeout,
    Duration readTimeout,
    DataSize maxResponseSize,
    int maxRedirects,
    int concurrency,
    int minContentLength,
    double minHangulRatio,
    String userAgent
) {

    public ArticleCrawlProperties {
        if (concurrency <= 0) {
            throw new IllegalArgumentException("rss.crawl.concurrency는 0보다 커야 합니다.");
        }
    }
}
