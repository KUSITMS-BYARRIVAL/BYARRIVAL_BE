package com.byarrival.domain.route.service;

import com.byarrival.domain.route.repository.RouteRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연속 이용(스트릭, HOME-02)과 최장 연속 완주. 평일(월~금) 기준으로, 노선을 완주한 평일이 하루도 빠짐없이 이어진 일수다.
 * 주말은 건너뛴다(주말에 완주하지 않아도 끊기지 않고, 주말 완주는 일수에 넣지 않는다). 평일을 하루라도 빠지면 끊긴다.
 * 오늘 아직 완주하지 않았으면 직전 평일까지 이어진 일수를 센다.
 */
@Service
@RequiredArgsConstructor
public class RouteStreakService {

    private final RouteRepository routeRepository;

    @Transactional(readOnly = true)
    public int streakDays(Long userId, LocalDate today) {
        Set<LocalDate> completed = new HashSet<>(weekdaysOf(routeRepository.findCompletedServiceDates(userId, today)));
        LocalDate day = isWeekday(today) && completed.contains(today) ? today : previousWeekday(today);
        int streak = 0;
        while (completed.contains(day)) {
            streak++;
            day = previousWeekday(day);
        }
        return streak;
    }

    /**
     * 최장 연속 완주 일수. 연속 이용(streakDays)과 같은 평일 기준으로, 오늘까지 가장 길게 이어진 구간이다.
     */
    @Transactional(readOnly = true)
    public int longestStreakDays(Long userId, LocalDate today) {
        // 최신순으로 오므로 직전 평일로 거슬러 올라가며 센다
        List<LocalDate> completed = weekdaysOf(routeRepository.findCompletedServiceDates(userId, today));
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : completed) {
            run = previous != null && day.equals(previousWeekday(previous)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        return longest;
    }

    private static List<LocalDate> weekdaysOf(List<LocalDate> dates) {
        return dates.stream().filter(RouteStreakService::isWeekday).toList();
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static LocalDate previousWeekday(LocalDate date) {
        LocalDate day = date.minusDays(1);
        while (!isWeekday(day)) {
            day = day.minusDays(1);
        }
        return day;
    }
}
