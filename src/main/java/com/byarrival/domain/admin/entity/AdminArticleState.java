package com.byarrival.domain.admin.entity;

/**
 * 어드민 목록의 기사 상태.
 * ERROR는 배치가 끝났는데 요약이나 퀴즈가 없는 기사와 원문이 삭제된 기사다.
 * DUPLICATE(같은 사건으로 추천에서 뺀 기사)와 PROCESSING(배치 처리 중)은 오류로 세지 않는다.
 */
public enum AdminArticleState {
    EXPOSED,
    HIDDEN,
    ERROR,
    DUPLICATE,
    PROCESSING
}
