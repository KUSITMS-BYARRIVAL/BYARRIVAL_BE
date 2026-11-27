package com.byarrival.domain.user.entity;

import java.time.DayOfWeek;
import java.util.Collection;

/**
 * 이동하는 요일 묶음. 요일마다 비트 하나(월=1 … 일=64)로 저장한다.
 */
public record CommuteDays(int bits) {

    public static final CommuteDays NONE = new CommuteDays(0);

    public CommuteDays {
        if (bits < 0 || bits > 127) {
            throw new IllegalArgumentException("이동 요일 비트는 0~127이어야 합니다: " + bits);
        }
    }

    public static CommuteDays of(Collection<DayOfWeek> days) {
        int bits = 0;
        for (DayOfWeek day : days) {
            bits |= bit(day);
        }
        return new CommuteDays(bits);
    }

    public boolean contains(DayOfWeek day) {
        return (bits & bit(day)) != 0;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
