package com.byarrival.domain.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TodayRouteResponse(
    TodayRouteState state,
    @Schema(description = "오늘 노선. NONE·EMPTY면 null") RouteResponse route
) {

    public static TodayRouteResponse none() {
        return new TodayRouteResponse(TodayRouteState.NONE, null);
    }

    public static TodayRouteResponse empty() {
        return new TodayRouteResponse(TodayRouteState.EMPTY, null);
    }

    public static TodayRouteResponse from(RouteResponse route) {
        return new TodayRouteResponse(TodayRouteState.valueOf(route.status().name()), route);
    }
}
