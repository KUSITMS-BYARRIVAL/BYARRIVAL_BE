package com.byarrival.domain.feed.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.feed.dto.response.FeedResponse;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 대상 RSS Feed를 등록하고 활성 상태를 관리한다.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int MAX_PRESS_NAME_LENGTH = 50;
    private static final int MAX_RSS_CATEGORY_LENGTH = 50;
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    // V1__init.sql의 feed_url UNIQUE에 PostgreSQL이 붙인 이름
    static final String FEED_URL_UNIQUE_CONSTRAINT = "rss_feed_feed_url_key";

    private final RssFeedRepository rssFeedRepository;
    private final FeedUrlValidator feedUrlValidator;

    // URL 검증의 DNS 조회가 트랜잭션을 붙잡지 않도록 트랜잭션을 걸지 않는다.
    // 중복 확인과 저장은 각각 Repository 트랜잭션에서 실행되고, 그 사이의 중복은 UNIQUE 제약이 막는다.
    public FeedResponse register(String pressName, String feedUrl, String rssCategory) {
        validateText(pressName, MAX_PRESS_NAME_LENGTH, false);
        validateText(rssCategory, MAX_RSS_CATEGORY_LENGTH, true);
        String normalizedUrl = feedUrlValidator.validate(feedUrl).toString();
        if (rssFeedRepository.existsByFeedUrl(normalizedUrl)) {
            throw new BusinessException(FeedErrorCode.DUPLICATE_FEED_URL);
        }

        RssFeed feed = RssFeed.create(pressName.strip(), normalizedUrl, stripOrNull(rssCategory));
        try {
            return FeedResponse.from(rssFeedRepository.saveAndFlush(feed));
        } catch (DataIntegrityViolationException e) {
            if (isFeedUrlUniqueViolation(e)) {
                throw new BusinessException(FeedErrorCode.DUPLICATE_FEED_URL);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<RssFeed> getActiveFeeds() {
        return rssFeedRepository.findAllByActiveTrue();
    }

    @Transactional
    public void deactivate(Long feedId) {
        RssFeed feed = rssFeedRepository.findById(feedId)
            .orElseThrow(() -> new BusinessException(FeedErrorCode.FEED_NOT_FOUND));
        feed.deactivate();
    }

    private void validateText(String value, int maxLength, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return;
            }
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        String stripped = value.strip();
        if ((!nullable && stripped.isEmpty()) || stripped.length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private boolean isFeedUrlUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIQUE_VIOLATION_SQL_STATE.equals(violation.getSQLState())
                    && FEED_URL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    private String stripOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
