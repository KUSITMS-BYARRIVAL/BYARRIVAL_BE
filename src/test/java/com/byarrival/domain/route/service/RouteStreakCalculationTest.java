package com.byarrival.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.route.service.RouteStreakService.StreakDays;
import com.byarrival.domain.user.dto.CommuteSchedule;
import com.byarrival.domain.user.entity.CommuteDays;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 스트릭 계산 규칙(HOME-02). 2026-11-02가 월요일이다.
 */
class RouteStreakCalculationTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final CommuteSchedule WEEKDAYS = schedule(
        EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
    private static final CommuteSchedule NO_COMMUTE = schedule(EnumSet.noneOf(DayOfWeek.class));

    @Test
    void 완주_기록이_없으면_0일이다() {
        assertThat(RouteStreakService.calculate(Set.of(), WEEKDAYS, MON)).isEqualTo(new StreakDays(0, 0));
    }

    @Test
    void 이동하는_요일에_완주하지_않으면_끊긴다() {
        // 월·화 완주, 수 미완주, 목 완주. 오늘은 금
        StreakDays streak = RouteStreakService.calculate(days(0, 1, 3), WEEKDAYS, MON.plusDays(4));

        assertThat(streak).isEqualTo(new StreakDays(1, 2));
    }

    @Test
    void 쉬는_날은_완주하지_않아도_끊기지_않고_완주하면_일수에_넣는다() {
        // 목·금 완주, 토 완주, 일 미완주, 월 완주. 오늘은 월
        StreakDays streak = RouteStreakService.calculate(days(3, 4, 5, 7), WEEKDAYS, MON.plusDays(7));

        assertThat(streak.current()).isEqualTo(4);
    }

    @Test
    void 오늘은_아직_완주하지_않아도_끊지_않고_어제까지_센다() {
        // 월·화 완주. 오늘(수)은 이동하는 요일이지만 아직 완주 전
        StreakDays streak = RouteStreakService.calculate(days(0, 1), WEEKDAYS, MON.plusDays(2));

        assertThat(streak.current()).isEqualTo(2);
    }

    @Test
    void 완주_없는_날이_7일째까지는_이어지고_8일째에_끊긴다() {
        // 일요일에 완주하고 월요일(1일째)부터 완주가 없다. 이동하는 요일은 없다
        Set<LocalDate> completed = days(-1);

        // 일요일(7일째)까지는 이어진다
        assertThat(RouteStreakService.calculate(completed, NO_COMMUTE, MON.plusDays(6)).current()).isEqualTo(1);
        // 다음 주 월요일(8일째)이 되면 끊긴다
        assertThat(RouteStreakService.calculate(completed, NO_COMMUTE, MON.plusDays(7)).current()).isZero();
        assertThat(RouteStreakService.calculate(completed, NO_COMMUTE, MON.plusDays(7)).longest()).isEqualTo(1);
    }

    @Test
    void 완주하면_완주_없는_날을_다음_날부터_다시_센다() {
        // 월 완주, 6일 쉬고 다음 주 월 완주, 다시 6일 쉬고 그다음 주 월이 오늘(완주 없는 날은 7일째에도 이어진다)
        StreakDays streak = RouteStreakService.calculate(days(0, 7), NO_COMMUTE, MON.plusDays(14));

        assertThat(streak.current()).isEqualTo(2);
    }

    @Test
    void 그날_바꾼_요일_설정은_다음_서비스일부터_적용한다() {
        // 원래 월~금 이동. 수요일(오늘)에 수요일을 쉬는 날로 바꿨다 → 오늘은 여전히 이동하는 요일
        CommuteDays weekdays = CommuteDays.of(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        CommuteDays withoutWednesday = CommuteDays.of(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        CommuteSchedule changed = CommuteSchedule.of(Map.of(LocalDate.of(1970, 1, 1), weekdays,
            MON.plusDays(3), withoutWednesday), withoutWednesday);

        assertThat(changed.isCommuteDay(MON.plusDays(2))).isTrue();
        assertThat(changed.isCommuteDay(MON.plusDays(9))).isFalse();
        // 월·화 완주, 수 미완주. 목요일 기준으로는 수요일이 이동하는 요일이었으므로 끊긴다
        assertThat(RouteStreakService.calculate(days(0, 1), changed, MON.plusDays(3)).current()).isZero();
        // 다음 주 월·화 완주. 그 주 수요일은 쉬는 날이라 목요일 기준으로도 끊기지 않는다
        assertThat(RouteStreakService.calculate(days(7, 8), changed, MON.plusDays(10)).current()).isEqualTo(2);
    }

    @Test
    void 이력이_없으면_현재_설정을_과거에도_적용한다() {
        CommuteSchedule current = CommuteSchedule.of(Map.of(), CommuteDays.of(List.of(DayOfWeek.MONDAY)));

        assertThat(current.isCommuteDay(LocalDate.of(2020, 1, 6))).isTrue();
        assertThat(current.isCommuteDay(LocalDate.of(2020, 1, 7))).isFalse();
    }

    private static CommuteSchedule schedule(Set<DayOfWeek> days) {
        return CommuteSchedule.of(Map.of(), CommuteDays.of(days));
    }

    // MON부터 며칠 뒤인지로 완주한 날을 만든다
    private static Set<LocalDate> days(int... offsets) {
        Set<LocalDate> dates = new HashSet<>();
        for (int offset : offsets) {
            dates.add(MON.plusDays(offset));
        }
        return dates;
    }
}
