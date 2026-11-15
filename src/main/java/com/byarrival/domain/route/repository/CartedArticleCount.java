package com.byarrival.domain.route.repository;

/**
 * 기사별로 그 기사를 담은 사용자 수.
 */
public interface CartedArticleCount {

    Long getArticleId();

    Long getUserCount();
}
