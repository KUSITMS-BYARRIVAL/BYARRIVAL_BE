package com.byarrival.domain.feed.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * RSS Feed 호출 제한값
 */
@ConfigurationProperties(prefix = "rss.fetch")
public record RssFetchProperties(
    Duration connectTimeout,
    Duration readTimeout,
    DataSize maxResponseSize,
    int maxRedirects
) {
}
