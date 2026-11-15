package com.byarrival.domain.route.dto.response;

import com.byarrival.domain.route.entity.RouteStatus;

public record RideResponse(
    Long routeId,
    RouteStatus status,
    RideProgressResponse progress
) {
}
