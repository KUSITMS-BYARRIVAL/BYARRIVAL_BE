package com.byarrival.domain.article.entity;

/**
 * 기사 가공 단계
 */
public enum ArticleProcessStatus {
    COLLECTED,
    CLEANED,
    SUMMARIZED,
    READY,
    FAILED
}
