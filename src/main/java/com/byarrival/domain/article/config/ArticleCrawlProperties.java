package com.byarrival.domain.article.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 기사 원문 크롤링 제한값
 *
 * @param connectTimeout   연결 timeout. 0이면 무제한이 되므로 1ms 이상만 허용한다
 * @param readTimeout      응답을 한 번 읽을 때 기다리는 시간. 1ms 이상
 * @param requestTimeout   리다이렉트와 본문 읽기를 합친 요청 한 건의 전체 시간 상한. 데이터를 조금씩 흘려보내는
 *                         서버 때문에 크롤링이 끝나지 않는 것을 막는다. 1ms 이상
 * @param concurrency      동시에 크롤링하는 기사 수
 * @param minContentLength 정제한 본문이 이보다 짧으면 본문을 찾지 못한 것으로 본다
 * @param minHangulRatio   제목·본문 글자 중 한글 비율이 이보다 낮으면 외국어 기사로 보고 뺀다(0~1)
 * @param userAgent        크롤링 요청의 User-Agent
 */
@ConfigurationProperties(prefix = "rss.crawl")
public record ArticleCrawlProperties(
    Duration connectTimeout,
    Duration readTimeout,
    Duration requestTimeout,
    DataSize maxResponseSize,
    int maxRedirects,
    int concurrency,
    int minContentLength,
    double minHangulRatio,
    String userAgent
) {

    public ArticleCrawlProperties {
        requirePositiveMillis(connectTimeout, "connect-timeout");
        requirePositiveMillis(readTimeout, "read-timeout");
        requirePositiveMillis(requestTimeout, "request-timeout");
        if (maxResponseSize == null || maxResponseSize.toBytes() < 1 || maxResponseSize.toBytes() >= Integer.MAX_VALUE) {
            // 응답을 byte 배열 하나로 읽으므로 int 범위 안이어야 한다
            throw new IllegalArgumentException("rss.crawl.max-response-size는 1B 이상 2GB 미만이어야 합니다.");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("rss.crawl.concurrency는 0보다 커야 합니다.");
        }
        if (minHangulRatio < 0 || minHangulRatio > 1) {
            throw new IllegalArgumentException("rss.crawl.min-hangul-ratio는 0 이상 1 이하여야 합니다.");
        }
    }

    // HttpURLConnection은 timeout 0을 무제한으로 본다. int 범위를 넘으면 설정할 수 없다.
    private static void requirePositiveMillis(Duration timeout, String name) {
        if (timeout == null || timeout.toMillis() < 1 || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rss.crawl." + name + "는 1ms 이상이어야 합니다.");
        }
    }
}
