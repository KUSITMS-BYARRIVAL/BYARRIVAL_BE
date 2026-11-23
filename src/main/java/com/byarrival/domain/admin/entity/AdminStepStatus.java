package com.byarrival.domain.admin.entity;

/**
 * 수집·요약·퀴즈 단계 상태. SKIPPED는 숨김이나 중복 제외로 그 단계를 판단하지 않는 경우다.
 */
public enum AdminStepStatus {
    SUCCESS,
    FAILED,
    WAITING,
    SKIPPED
}
