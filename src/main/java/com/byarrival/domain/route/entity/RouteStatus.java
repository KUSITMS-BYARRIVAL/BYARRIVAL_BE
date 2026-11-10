package com.byarrival.domain.route.entity;

/**
 * 노선 상태. 만료(EXPIRED)는 저장하지 않고 expires_at과 현재 시각을 비교해 판단한다.
 */
public enum RouteStatus {
    CREATED,
    BOARDING,
    IN_PROGRESS,
    COMPLETED,
    EXPIRED
}
