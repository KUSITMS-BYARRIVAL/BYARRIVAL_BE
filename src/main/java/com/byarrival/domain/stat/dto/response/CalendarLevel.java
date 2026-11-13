package com.byarrival.domain.stat.dto.response;

/**
 * 기록 캘린더 단계(STAT-02). NONE 회색(앱 미접속), ACCESSED 초록1(앱 접속만), PARTIAL 초록2(일부 완독),
 * COMPLETED 초록3(노선 완주)
 */
public enum CalendarLevel {
    NONE,
    ACCESSED,
    PARTIAL,
    COMPLETED
}
