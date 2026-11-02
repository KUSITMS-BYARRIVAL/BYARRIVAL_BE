package com.byarrival.domain.feed.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.config.RssFetchProperties;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RSS Feed URL을 호출해 원문을 받아 온다. 호출 직전과 리다이렉트마다 URL을 다시 검사한다.
 */
@Component
@RequiredArgsConstructor
public class RssFeedFetcher {

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final FeedUrlValidator feedUrlValidator;
    private final RssFetchProperties properties;

    public byte[] fetch(String feedUrl) {
        URI uri = feedUrlValidator.validate(feedUrl);
        for (int redirects = 0; ; redirects++) {
            HttpURLConnection connection = open(uri);
            try {
                int status = connection.getResponseCode();
                if (REDIRECT_STATUSES.contains(status)) {
                    if (redirects >= properties.maxRedirects()) {
                        throw failed("리다이렉트 횟수가 " + properties.maxRedirects() + "회를 넘었습니다.");
                    }
                    uri = feedUrlValidator.validate(resolveLocation(uri, connection));
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw failed("응답 상태 코드 " + status);
                }
                return readLimited(connection);
            } catch (IOException e) {
                throw failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(URI uri) {
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
            connection.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
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

    private BusinessException failed(String message) {
        return new BusinessException(FeedErrorCode.FEED_FETCH_FAILED, message);
    }
}
