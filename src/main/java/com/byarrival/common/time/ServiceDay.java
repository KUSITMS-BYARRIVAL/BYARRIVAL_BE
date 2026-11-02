package com.byarrival.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 서비스일 계산. 서비스일은 KST 04:00에 바뀌며 04:00부터 다음 날 03:59까지가 하루다.
 */
public final class ServiceDay {

    private static final int START_HOUR = 4;

    private ServiceDay() {
    }

    public static LocalDate of(LocalDateTime dateTime) {
        return dateTime.minusHours(START_HOUR).toLocalDate();
    }

    public static LocalDate today(Clock clock) {
        return of(LocalDateTime.now(clock));
    }
}
