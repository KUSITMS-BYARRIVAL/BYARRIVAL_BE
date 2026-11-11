package com.byarrival.domain.user.entity;

import java.time.LocalTime;

/**
 * 한 요일의 이동 시작 시각과 이동시간. 설정하지 않은 요일은 이동하지 않는 날(출근 알림 없음)이다.
 */
public record DailyCommute(LocalTime startTime, int minutes) {

    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 120;
    public static final int MINUTES_STEP = 5;

    public static boolean isValidMinutes(int minutes) {
        return minutes >= MIN_MINUTES && minutes <= MAX_MINUTES && minutes % MINUTES_STEP == 0;
    }
}
