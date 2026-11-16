package com.byarrival.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * @param sigunguId 근무 지역 목록(GET /regions)의 시/군/구 id
 */
public record RegionRequest(
    @NotNull Integer sigunguId
) {
}
