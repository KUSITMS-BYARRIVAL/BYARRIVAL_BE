package com.byarrival.domain.user.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이용 제한 기간(MEMBER-03). 영구 제한은 끝나는 시각이 없다.
 */
@Getter
@RequiredArgsConstructor
public enum RestrictionPeriod {

    DAYS_3(3),
    DAYS_7(7),
    DAYS_30(30),
    PERMANENT(null);

    private final Integer days;

    /**
     * @return 제한이 끝나는 시각. 영구 제한이면 null
     */
    public LocalDateTime endsAt(LocalDateTime startsAt) {
        return days == null ? null : startsAt.plusDays(days);
    }
}
