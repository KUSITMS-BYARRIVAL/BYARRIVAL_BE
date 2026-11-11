package com.byarrival.domain.user.dto.request;

import com.byarrival.domain.user.entity.TravelType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/**
 * 내 정보 수정. 보낸 항목만 바꾸고 보내지 않은(null) 항목은 그대로 둔다.
 *
 * @param commuteStartTime      기본 이동 시작 시각 (HH:mm)
 * @param defaultCommuteMinutes 기본 이동시간(분)
 */
public record UserUpdateRequest(
    String nickname,
    TravelType travelType,
    @JsonFormat(pattern = "HH:mm") LocalTime commuteStartTime,
    Integer defaultCommuteMinutes
) {
}
