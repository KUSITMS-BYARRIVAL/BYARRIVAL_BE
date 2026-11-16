package com.byarrival.domain.region.dto.response;

/**
 * 회원이 고른 근무 지역. 마이 화면에는 "서울 강남구"처럼 sidoName과 sigunguName을 이어 보여 준다.
 */
public record RegionResponse(
    Short sidoId,
    String sidoName,
    Integer sigunguId,
    String sigunguName
) {
}
