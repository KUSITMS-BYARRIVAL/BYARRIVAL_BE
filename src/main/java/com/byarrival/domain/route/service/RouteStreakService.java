package com.byarrival.domain.route.service;

import com.byarrival.domain.route.repository.RouteRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연속 이용(스트릭, HOME-02). 노선을 완주한 서비스일이 하루도 빠짐없이 이어진 일수다.
 * 주말·쉬는 날도 빠지면 끊긴다. 오늘 아직 완주하지 않았으면 어제까지 이어진 일수를 센다.
 */
@Service
@RequiredArgsConstructor
public class RouteStreakService {

    private final RouteRepository routeRepository;

    @Transactional(readOnly = true)
    public int streakDays(Long userId, LocalDate today) {
        Set<LocalDate> completed = new HashSet<>(routeRepository.findCompletedServiceDates(userId, today));
        LocalDate day = completed.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (completed.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /**
     * 최장 연속 완주 일수. 연속 이용(streakDays)과 같은 기준으로, 오늘까지 완주한 서비스일 중 하루도 빠짐없이 이어진 가장 긴 구간이다.
     */
    @Transactional(readOnly = true)
    public int longestStreakDays(Long userId, LocalDate today) {
        List<LocalDate> completed = routeRepository.findCompletedServiceDates(userId, today);
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        // 최신순으로 오므로 하루씩 거슬러 올라가며 센다
        for (LocalDate day : completed) {
            run = previous != null && day.equals(previous.minusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        return longest;
    }
}
