package com.byarrival.domain.region.dto.response;

import java.util.List;

/**
 * 시/도와 그 안의 시/군/구 목록. 시/군/구는 이름순이다.
 *
 * @param name     화면에 보이는 약칭. 예: 서울
 * @param fullName 예: 서울특별시
 */
public record RegionSidoResponse(
    Short sidoId,
    String name,
    String fullName,
    List<SigunguResponse> sigungus
) {

    public record SigunguResponse(
        Integer sigunguId,
        String name
    ) {
    }
}
