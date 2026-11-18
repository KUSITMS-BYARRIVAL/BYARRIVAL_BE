package com.byarrival.domain.route.repository;

/**
 * 마지막 활동 뒤 한동안 움직임이 없는 미완주 노선.
 */
public interface IdleRoute {

    Long getRouteId();

    Long getUserId();

    Long getRemainingStops();
}
