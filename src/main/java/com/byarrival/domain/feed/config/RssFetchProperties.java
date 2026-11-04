package com.byarrival.domain.feed.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * RSS Feed 호출 제한값과 자동 비활성화 기준(연속 실패 횟수)
 */
@ConfigurationProperties(prefix = "rss.fetch")
public record RssFetchProperties(
    Duration connectTimeout,
    Duration readTimeout,
    DataSize maxResponseSize,
    int maxRedirects,
    int maxConsecutiveFailures
) {
}
