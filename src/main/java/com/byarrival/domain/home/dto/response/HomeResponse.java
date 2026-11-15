package com.byarrival.domain.home.dto.response;

import com.byarrival.domain.route.dto.response.TodayRouteResponse;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 오늘의 홈(HOME-01). 영역별로 따로 조회해, 한 영역이 실패하면 그 영역만 null이고 나머지는 그대로 보여 준다.
 */
public record HomeResponse(
    @Schema(description = "서비스일(04:00 경계)") LocalDate serviceDate,
    @Schema(description = "오늘 탑승 상태. GET /routes/today와 같다. 조회 실패 시 null") TodayRouteResponse todayRoute,
    @Schema(description = "연속 이용 일수(평일 완주 기준, HOME-02). 기록이 없으면 0, 조회 실패 시 null") Integer streakDays,
    @Schema(description = "스몰토크 추천 미리보기 최대 3개(HOME-03). 비어 있으면 영역 미노출, 조회 실패 시 null")
    List<SmalltalkResponse> smalltalks,
    @Schema(description = "오늘(서비스일) 한 번이라도 앱을 쓴 회원 수(HOME-04). 조회 실패 시 null이며 카드를 숨긴다")
    Long todayVisitorCount,
    @Schema(description = "출근길 공감대 TOP3(HOME-05). 내가 고른 직군 칩(고른 순서) 다음에 전체 칩. 집계 실패 시 null이며 섹션을 숨긴다")
    List<PopularChipResponse> popular
) {
}
