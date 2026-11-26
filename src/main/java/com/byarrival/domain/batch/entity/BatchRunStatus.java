package com.byarrival.domain.batch.entity;

public enum BatchRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    // 운영자가 새로고침해 다른 실행으로 대체된 배치
    REPLACED
}
