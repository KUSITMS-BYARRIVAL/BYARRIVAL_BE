package com.byarrival.domain.admin.entity;

/**
 * 재생성 단계 결과(ARTICLE-06). SKIPPED는 요청하지 않았거나 앞 단계가 실패해 하지 않은 단계다.
 */
public enum RegenerateStep {
    SUCCESS,
    FAILED,
    SKIPPED
}
