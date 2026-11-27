package com.byarrival.domain.user.dto;

import com.byarrival.domain.user.entity.CommuteDays;
import java.time.LocalDate;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 서비스일마다 이동하는 요일(HOME-02 스트릭 대상일). 그날 적용 중이던 요일 설정으로 판단한다.
 * 변경 이력이 없으면 현재 설정을 모든 날짜에 적용한다.
 */
public final class CommuteSchedule {

    private final NavigableMap<LocalDate, CommuteDays> history;
    private final CommuteDays current;

    private CommuteSchedule(NavigableMap<LocalDate, CommuteDays> history, CommuteDays current) {
        this.history = history;
        this.current = current;
    }

    /**
     * @param history 적용 시작 서비스일 → 그날부터의 이동 요일
     * @param current 현재 요일 설정. 이력이 없을 때 쓴다
     */
    public static CommuteSchedule of(Map<LocalDate, CommuteDays> history, CommuteDays current) {
        return new CommuteSchedule(new TreeMap<>(history), current);
    }

    public boolean isCommuteDay(LocalDate serviceDate) {
        if (history.isEmpty()) {
            return current.contains(serviceDate.getDayOfWeek());
        }
        Entry<LocalDate, CommuteDays> entry = history.floorEntry(serviceDate);
        // 이력은 바꾸기 전 설정을 가장 이른 날짜로 먼저 남기므로 그보다 앞선 날은 없다. 혹시 있으면 가장 이른 설정을 쓴다
        CommuteDays days = entry == null ? history.firstEntry().getValue() : entry.getValue();
        return days.contains(serviceDate.getDayOfWeek());
    }
}
