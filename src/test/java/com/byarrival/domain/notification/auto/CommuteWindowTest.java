package com.byarrival.domain.notification.auto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CommuteWindowTest {

    @Test
    void 출근_전_알림은_지금보다_뒤의_출근_시각을_허용_시간만큼_찾는다() {
        // 수요일 07:50에 출근 10분 전 알림 → 출근 시각 07:51~08:00
        assertThat(CommuteWindow.of(LocalDateTime.of(2030, 1, 9, 7, 50, 30), -10, 10))
            .containsExactly(new CommuteWindow(DayOfWeek.WEDNESDAY, LocalTime.of(7, 51), LocalTime.of(8, 0)));
    }

    @Test
    void 출근_후_알림은_지금보다_앞의_출근_시각을_찾는다() {
        // 수요일 11:00에 출근 3시간 뒤 알림 → 출근 시각 07:51~08:00
        assertThat(CommuteWindow.of(LocalDateTime.of(2030, 1, 9, 11, 0), 180, 10))
            .containsExactly(new CommuteWindow(DayOfWeek.WEDNESDAY, LocalTime.of(7, 51), LocalTime.of(8, 0)));
    }

    @Test
    void 자정을_넘으면_요일별로_나눈다() {
        // 수요일 00:03에 출근 0분 뒤 → 화요일 23:54~23:59와 수요일 00:00~00:03
        assertThat(CommuteWindow.of(LocalDateTime.of(2030, 1, 9, 0, 3), 0, 10)).containsExactly(
            new CommuteWindow(DayOfWeek.TUESDAY, LocalTime.of(23, 54), LocalTime.of(23, 59)),
            new CommuteWindow(DayOfWeek.WEDNESDAY, LocalTime.MIDNIGHT, LocalTime.of(0, 3)));
    }
}
