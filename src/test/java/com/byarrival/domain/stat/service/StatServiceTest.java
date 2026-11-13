package com.byarrival.domain.stat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.byarrival.domain.event.service.UsageEventQueryService;
import com.byarrival.domain.route.service.RouteRecordService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.stat.dto.response.StatPeriod;
import com.byarrival.domain.stat.dto.response.StatSummaryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 요약의 '오늘'은 서비스일(KST 04:00 경계)이다. 앱 접속 날짜(KST 자정)와 다른 기준인지 경계 시각으로 확인한다.
 */
class StatServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;

    @Test
    void 새벽_3시59분의_오늘은_전날_서비스일이다() {
        StatSummaryResponse summary = service(LocalDateTime.of(2026, 11, 12, 3, 59)).getSummary(USER_ID, StatPeriod.MONTH);

        assertThat(summary.to()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void 새벽_4시부터는_그날_서비스일이다() {
        StatSummaryResponse summary = service(LocalDateTime.of(2026, 11, 12, 4, 0)).getSummary(USER_ID, StatPeriod.MONTH);

        assertThat(summary.to()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void 월요일_새벽_3시59분의_이번_주는_지난주다() {
        // 11-16(월) 03:59의 서비스일은 11-15(일)이므로 이번 주는 11-09(월)부터다
        StatSummaryResponse summary = service(LocalDateTime.of(2026, 11, 16, 3, 59)).getSummary(USER_ID, StatPeriod.WEEK);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(summary.to()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    private static StatService service(LocalDateTime now) {
        RouteRecordService routeRecordService = mock(RouteRecordService.class);
        given(routeRecordService.findDailyRecords(eq(USER_ID), any(), any())).willReturn(List.of());
        return new StatService(routeRecordService, mock(UsageEventQueryService.class), mock(RouteStreakService.class),
            Clock.fixed(now.atZone(KST).toInstant(), KST));
    }
}
