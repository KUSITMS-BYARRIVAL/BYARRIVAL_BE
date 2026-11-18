package com.byarrival.domain.notification.auto;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 지금 알림을 보낼 차례인 출근 시각의 범위. 알림 시각 = 출근 시각 + offset이므로 출근 시각은 (지금 - offset - grace, 지금 - offset]이다.
 * 출근 시각은 분 단위라 양 끝을 포함하는 분 범위로 바꾸고, 자정을 넘으면 요일별로 나눈다.
 *
 * @param day  출근 요일
 * @param from 이 시각부터(포함)
 * @param to   이 시각까지(포함)
 */
public record CommuteWindow(DayOfWeek day, LocalTime from, LocalTime to) {

    private static final LocalTime LAST_MINUTE = LocalTime.of(23, 59);

    /**
     * @param offsetMinutes 출근 시각 기준 알림 시각. 출근 전이면 음수다
     */
    public static List<CommuteWindow> of(LocalDateTime now, int offsetMinutes, int graceMinutes) {
        LocalDateTime end = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(offsetMinutes);
        LocalDateTime start = end.minusMinutes(graceMinutes - 1L);
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return List.of(new CommuteWindow(end.getDayOfWeek(), start.toLocalTime(), end.toLocalTime()));
        }
        return List.of(
            new CommuteWindow(start.getDayOfWeek(), start.toLocalTime(), LAST_MINUTE),
            new CommuteWindow(end.getDayOfWeek(), LocalTime.MIDNIGHT, end.toLocalTime()));
    }
}
