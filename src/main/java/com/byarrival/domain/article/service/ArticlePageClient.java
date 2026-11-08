package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.feed.service.FeedUrlValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기사 원문 페이지를 호출한다. 호출 직전과 리다이렉트마다 내부망 주소인지 다시 검사하고, 응답 크기와
 * 요청 한 건의 전체 시간(리다이렉트·본문 읽기 포함)을 제한한다.
 */
@Component
@RequiredArgsConstructor
public class ArticlePageClient {

    private static final int READ_CHUNK_SIZE = 8192;
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
        long deadline = System.nanoTime() + properties.requestTimeout().toNanos();
        URI uri = urlValidator.validate(url, MAX_URL_LENGTH);
        for (int redirects = 0; ; redirects++) {
            HttpURLConnection connection = open(uri, formBody, deadline);
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
                return new Page(uri.toString(), readLimited(connection, deadline), charsetOf(connection));
            } catch (IOException e) {
                throw failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(URI uri, byte[] formBody, long deadline) {
        int remaining = remainingMillis(deadline);
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            // 남은 시간보다 오래 기다리지 않는다
            connection.setConnectTimeout((int) Math.min(properties.connectTimeout().toMillis(), remaining));
            connection.setReadTimeout((int) Math.min(properties.readTimeout().toMillis(), remaining));
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

    // 조각마다 전체 시간 상한을 확인한다. 조각 하나는 read timeout까지만 기다린다.
    private byte[] readLimited(HttpURLConnection connection, long deadline) throws IOException {
        int maxBytes = Math.toIntExact(properties.maxResponseSize().toBytes());
        try (InputStream body = connection.getInputStream()) {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_CHUNK_SIZE];
            int read;
            while ((read = body.read(buffer)) != -1) {
                content.write(buffer, 0, read);
                if (content.size() > maxBytes) {
                    throw failed("응답 크기가 " + properties.maxResponseSize() + "를 넘었습니다.");
                }
                remainingMillis(deadline);
            }
            return content.toByteArray();
        }
    }

    private int remainingMillis(long deadline) {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) {
            throw failed("요청 시간이 " + properties.requestTimeout() + "를 넘었습니다.");
        }
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    // Content-Type 헤더의 charset. 없거나 지원하지 않는 이름이면 null(본문의 meta 태그로 판단한다)
    private static String charsetOf(HttpURLConnection connection) {
        String contentType = connection.getContentType();
        if (contentType == null) {
            return null;
        }
        for (String parameter : contentType.split(";")) {
            String[] pair = parameter.strip().split("=", 2);
            if (pair.length == 2 && pair[0].strip().equalsIgnoreCase("charset")) {
                String charset = pair[1].strip().replace("\"", "");
                try {
                    return Charset.isSupported(charset) ? charset : null;
                } catch (IllegalCharsetNameException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static BusinessException failed(String message) {
        return new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED, message);
    }

    /**
     * @param url     리다이렉트를 따라간 뒤의 최종 주소
     * @param charset 응답 헤더가 알려 준 charset. 없으면 null
     */
    public record Page(String url, byte[] body, String charset) {

        public Page(String url, byte[] body) {
            this(url, body, null);
        }
    }
}
