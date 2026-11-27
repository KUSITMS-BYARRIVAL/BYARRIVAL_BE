package com.byarrival.domain.route.service;

import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.route.repository.RouteRepository.UserCompletedDate;
import com.byarrival.domain.user.dto.CommuteSchedule;
import com.byarrival.domain.user.service.CommuteDayService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연속 이용(스트릭, HOME-02)과 최장 연속 완주. 서비스일(04:00 경계) 기준으로 센다.
 * <ul>
 *     <li>대상일은 그날 이동하는 요일로 정한 날이다(이동시간 5분 이상). 평일·공휴일은 따지지 않는다.</li>
 *     <li>완주한 날은 대상일이 아니어도 일수에 넣는다.</li>
 *     <li>대상일에 완주하지 않으면 끊긴다.</li>
 *     <li>완주 없는 날이 7일 이어지면 8일째 되는 날(04:00)에 끊긴다. 완주 없는 첫날을 1일째로 센다.
 *     모든 요일을 쉬는 날로 정해도 일주일 넘게 쉬면 끊기게 하려는 것이다.</li>
 *     <li>오늘은 아직 끝나지 않았으므로, 완주 전이면 끊지 않고 어제까지 이어진 일수를 센다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RouteStreakService {

    static final int MAX_DAYS_WITHOUT_COMPLETION = 7;

    private final RouteRepository routeRepository;
    private final CommuteDayService commuteDayService;

    @Transactional(readOnly = true)
    public int streakDays(Long userId, LocalDate today) {
        return of(userId, today).current();
    }

    /**
     * 최장 연속 완주 일수. 연속 이용(streakDays)과 같은 기준으로, 오늘까지 가장 길게 이어진 구간이다.
     */
    @Transactional(readOnly = true)
    public int longestStreakDays(Long userId, LocalDate today) {
        return of(userId, today).longest();
    }

    /**
     * 그 서비스일이 스트릭 대상일(이동하는 요일)인지. 그날 적용 중이던 요일 설정으로 판단한다.
     */
    @Transactional(readOnly = true)
    public boolean isStreakDay(Long userId, LocalDate serviceDate) {
        return commuteDayService.findSchedule(userId).isCommuteDay(serviceDate);
    }

    /**
     * 여러 회원의 현재·최장 연속 일수를 한 번에 구한다. 완주 기록이 없는 회원은 0일이다.
     */
    @Transactional(readOnly = true)
    public Map<Long, StreakDays> findStreaks(Collection<Long> userIds, LocalDate today) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<LocalDate>> completedByUser = new HashMap<>();
        for (UserCompletedDate row : routeRepository.findCompletedServiceDatesOf(userIds, today)) {
            completedByUser.computeIfAbsent(row.getUserId(), id -> new HashSet<>()).add(row.getServiceDate());
        }
        Map<Long, CommuteSchedule> schedules = commuteDayService.findSchedules(userIds);
        Map<Long, StreakDays> streaks = new HashMap<>();
        for (Long userId : userIds) {
            Set<LocalDate> completed = completedByUser.getOrDefault(userId, Set.of());
            CommuteSchedule schedule = schedules.get(userId);
            streaks.put(userId, schedule == null ? new StreakDays(0, 0) : calculate(completed, schedule, today));
        }
        return streaks;
    }

    private StreakDays of(Long userId, LocalDate today) {
        Set<LocalDate> completed = new HashSet<>(routeRepository.findCompletedServiceDates(userId, today));
        return calculate(completed, commuteDayService.findSchedule(userId), today);
    }

    /**
     * 가장 이른 완주일부터 오늘까지 하루씩 지나가며 이어진 일수를 센다. 마지막 구간이 현재 연속 일수다.
     */
    static StreakDays calculate(Set<LocalDate> completed, CommuteSchedule schedule, LocalDate today) {
        if (completed.isEmpty()) {
            return new StreakDays(0, 0);
        }
        LocalDate first = completed.stream().min(LocalDate::compareTo).orElseThrow();
        int run = 0;
        int longest = 0;
        int daysWithoutCompletion = 0;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            if (completed.contains(day)) {
                run++;
                longest = Math.max(longest, run);
                daysWithoutCompletion = 0;
                continue;
            }
            if (day.equals(today)) {
                // 오늘은 아직 끝나지 않았다
                break;
            }
            daysWithoutCompletion++;
            if (schedule.isCommuteDay(day) || daysWithoutCompletion >= MAX_DAYS_WITHOUT_COMPLETION) {
                run = 0;
            }
        }
        return new StreakDays(run, longest);
    }

    public record StreakDays(int current, int longest) {
    }
}
