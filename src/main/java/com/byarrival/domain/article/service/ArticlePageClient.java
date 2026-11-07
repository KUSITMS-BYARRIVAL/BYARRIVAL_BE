package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.feed.service.FeedUrlValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기사 원문 페이지를 호출한다. 호출 직전과 리다이렉트마다 내부망 주소인지 다시 검사하고, 응답 크기를 제한한다.
 */
@Component
@RequiredArgsConstructor
public class ArticlePageClient {

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    // RSS 기사 링크와 원문 주소는 Feed URL(500자)보다 길 수 있어 저장 컬럼 길이까지 허용한다
    private static final int MAX_URL_LENGTH = Article.MAX_ORIGINAL_URL_LENGTH;

    private final FeedUrlValidator urlValidator;
    private final ArticleCrawlProperties properties;

    public Page get(String url) {
        return request(url, null);
    }

    /**
     * application/x-www-form-urlencoded로 POST한다. 리다이렉트는 따라가지 않는다.
     */
    public Page postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        return request(url, body.getBytes(StandardCharsets.UTF_8));
    }

    private Page request(String url, byte[] formBody) {
        URI uri = urlValidator.validate(url, MAX_URL_LENGTH);
        for (int redirects = 0; ; redirects++) {
            HttpURLConnection connection = open(uri, formBody);
            try {
                int status = connection.getResponseCode();
                if (formBody == null && REDIRECT_STATUSES.contains(status)) {
                    if (redirects >= properties.maxRedirects()) {
                        throw failed("리다이렉트 횟수가 " + properties.maxRedirects() + "회를 넘었습니다.");
                    }
                    uri = urlValidator.validate(resolveLocation(uri, connection), MAX_URL_LENGTH);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw failed("응답 상태 코드 " + status);
                }
                return new Page(uri.toString(), readLimited(connection));
            } catch (IOException e) {
                throw failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(URI uri, byte[] formBody) {
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
            connection.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
            connection.setRequestProperty("User-Agent", properties.userAgent());
            if (formBody != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(formBody);
                }
            }
            return connection;
        } catch (IOException | IllegalArgumentException e) {
            throw failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String resolveLocation(URI current, HttpURLConnection connection) {
        String location = connection.getHeaderField("Location");
        if (location == null || location.isBlank()) {
            throw failed("리다이렉트 응답에 Location이 없습니다.");
        }
        try {
            return current.resolve(location.strip()).toString();
        } catch (IllegalArgumentException e) {
            throw failed("리다이렉트 Location 형식이 올바르지 않습니다.");
        }
    }

    private byte[] readLimited(HttpURLConnection connection) throws IOException {
        int maxBytes = Math.toIntExact(properties.maxResponseSize().toBytes());
        try (InputStream body = connection.getInputStream()) {
            byte[] content = body.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                throw failed("응답 크기가 " + properties.maxResponseSize() + "를 넘었습니다.");
            }
            return content;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static BusinessException failed(String message) {
        return new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED, message);
    }

    /**
     * @param url 리다이렉트를 따라간 뒤의 최종 주소
     */
    public record Page(String url, byte[] body) {
    }
}
