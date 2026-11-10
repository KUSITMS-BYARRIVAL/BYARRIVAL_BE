package com.byarrival.domain.route.dto.response;

/**
 * 탑승 시작 화면 분기.
 * NONE: 오늘 노선 없음(컨디션 선택), EMPTY: 추천 기사 0건(오늘 준비한 뉴스는 여기까지), CREATED: 카드 담는 중,
 * BOARDING: 보딩패스, IN_PROGRESS: 운행 중, COMPLETED: 완주, EXPIRED: 만료
 */
public enum TodayRouteState {
    NONE,
    EMPTY,
    CREATED,
    BOARDING,
    IN_PROGRESS,
    COMPLETED,
    EXPIRED
}
