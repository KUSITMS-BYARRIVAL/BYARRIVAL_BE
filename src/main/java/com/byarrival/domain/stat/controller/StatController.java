package com.byarrival.domain.stat.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.stat.dto.response.CalendarResponse;
import com.byarrival.domain.stat.dto.response.ConditionStatResponse;
import com.byarrival.domain.stat.dto.response.StatPeriod;
import com.byarrival.domain.stat.dto.response.StatSummaryResponse;
import com.byarrival.domain.stat.dto.response.StreakResponse;
import com.byarrival.domain.stat.dto.response.UsageGraphResponse;
import com.byarrival.domain.stat.service.StatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stat", description = "나의 기록")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @Operation(summary = "이용 기록 요약",
        description = "총 이용시간·읽은 기사 수·완주 횟수. WEEK는 이번 주(월~오늘), MONTH는 이번 달, YEAR는 올해, ALL은 전체. "
            + "날짜는 서비스일(04:00 경계) 기준이다. empty=true면 Empty UI.")
    @GetMapping("/summary")
    public ApiResponse<StatSummaryResponse> getSummary(
        @LoginUserId Long userId, @RequestParam(defaultValue = "WEEK") StatPeriod period) {
        return ApiResponse.ok(statService.getSummary(userId, period));
    }

    @Operation(summary = "연간 기록 잔디",
        description = "그 해 평일(월~금)만 날짜순으로 담는다. 단계: NONE 회색(앱 미접속), ACCESSED 초록1(앱 접속만), "
            + "PARTIAL 초록2(일부 완독), COMPLETED 초록3(완주). 완독·완주는 노선의 서비스일, 앱 접속은 KST 자정 기준 날짜로 센다. "
            + "future=true는 비활성. completedDays는 그 해 평일 완주 일수.")
    @GetMapping("/calendar")
    public ApiResponse<CalendarResponse> getCalendar(
        @LoginUserId Long userId, @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(statService.getCalendar(userId, year));
    }

    @Operation(summary = "연속 완주", description = "현재 연속 완주 일수와 최장 연속 완주 일수. 연속 이용(도착 화면)과 같은 기준이다.")
    @GetMapping("/streak")
    public ApiResponse<StreakResponse> getStreak(@LoginUserId Long userId) {
        return ApiResponse.ok(statService.getStreak(userId));
    }

    @Operation(summary = "컨디션 기록",
        description = "올해 완주한 날의 컨디션(맑음·보통·흐림·비) 분포와 이번 달 분포, 이번 달 가장 많았던 컨디션.")
    @GetMapping("/conditions")
    public ApiResponse<ConditionStatResponse> getConditions(@LoginUserId Long userId) {
        return ApiResponse.ok(statService.getConditions(userId));
    }

    @Operation(summary = "최근 6주 이용 그래프", description = "월요일 시작 주 단위. 마지막 항목이 이번 주다. empty=true면 초기 안내.")
    @GetMapping("/weekly")
    public ApiResponse<UsageGraphResponse> getWeeklyGraph(@LoginUserId Long userId) {
        return ApiResponse.ok(statService.getWeeklyGraph(userId));
    }
}
