package com.byarrival.domain.user.dto.request;

import com.byarrival.domain.user.entity.TravelType;

/**
 * 내 정보 수정. 보낸 항목만 바꾸고 보내지 않은(null) 항목은 그대로 둔다.
 * 출근(이동 시작) 시각은 요일별 이동 설정(PUT /users/me/weekly-commute)에서 바꾼다.
 *
 * @param defaultCommuteMinutes 기본 이동시간(분). 요일별 설정이 없는 날의 노선 목표 시간으로 쓴다.
 */
public record UserUpdateRequest(
    String nickname,
    TravelType travelType,
    Integer defaultCommuteMinutes
) {
}
