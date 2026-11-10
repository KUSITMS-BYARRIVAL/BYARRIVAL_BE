package com.byarrival.domain.route.entity;

/**
 * 오늘의 컨디션 (맑음·보통·흐림·비). 노선 추천의 HARD:EASY 비율을 정한다.
 */
public enum RouteCondition {
    SUNNY,
    NORMAL,
    CLOUDY,
    RAINY
}
