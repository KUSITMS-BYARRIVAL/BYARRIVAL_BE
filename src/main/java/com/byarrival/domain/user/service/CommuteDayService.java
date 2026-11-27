package com.byarrival.domain.user.service;

import com.byarrival.domain.user.dto.CommuteSchedule;
import com.byarrival.domain.user.entity.CommuteDays;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserCommuteDayHistory;
import com.byarrival.domain.user.repository.UserCommuteDayHistoryRepository;
import com.byarrival.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스트릭 대상일(이동하는 요일) 관리(HOME-02).
 * 요일 설정을 바꾸면 다음 서비스일부터 적용한다. 그 요일을 바꾸려면 그 전날(그 요일 04:00 전)까지 바꿔야 한다.
 * 노선 목표 시간과 출근 시각 알림은 지금처럼 바꾼 즉시 현재 설정을 따른다.
 */
@Service
@RequiredArgsConstructor
public class CommuteDayService {

    /** 처음 바꿀 때 바꾸기 전 설정을 이 날짜부터 적용한다. 배포 전 기간에도 그 설정을 쓰기 위해서다. */
    static final LocalDate BASELINE_DATE = LocalDate.of(1970, 1, 1);

    private final UserCommuteDayHistoryRepository historyRepository;
    private final UserRepository userRepository;

    /**
     * 요일 설정을 바꾸기 직전에 부른다. 회원 행을 잠근 트랜잭션 안에서 부른다.
     *
     * @param before 바꾸기 전 이동 요일
     * @param after  바꾼 뒤 이동 요일
     * @param today  바꾼 서비스일
     */
    @Transactional
    public void recordChange(Long userId, CommuteDays before, CommuteDays after, LocalDate today,
        LocalDateTime now) {
        if (!historyRepository.existsByUserId(userId)) {
            historyRepository.save(UserCommuteDayHistory.of(userId, BASELINE_DATE, before, now));
        }
        LocalDate effectiveFrom = today.plusDays(1);
        historyRepository.findByUserIdAndEffectiveFrom(userId, effectiveFrom)
            .ifPresentOrElse(history -> history.change(after, now),
                () -> historyRepository.save(UserCommuteDayHistory.of(userId, effectiveFrom, after, now)));
    }

    @Transactional(readOnly = true)
    public CommuteSchedule findSchedule(Long userId) {
        return findSchedules(List.of(userId)).getOrDefault(userId, CommuteSchedule.of(Map.of(), CommuteDays.NONE));
    }

    /**
     * 없는 회원은 빠진다.
     */
    @Transactional(readOnly = true)
    public Map<Long, CommuteSchedule> findSchedules(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<LocalDate, CommuteDays>> histories = new HashMap<>();
        for (UserCommuteDayHistory history : historyRepository.findAllByUserIdIn(userIds)) {
            histories.computeIfAbsent(history.getUserId(), id -> new HashMap<>())
                .put(history.getEffectiveFrom(), history.days());
        }
        Map<Long, CommuteSchedule> schedules = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            schedules.put(user.getId(), CommuteSchedule.of(histories.getOrDefault(user.getId(), Map.of()),
                commuteDaysOf(user)));
        }
        return schedules;
    }

    public static CommuteDays commuteDaysOf(User user) {
        return CommuteDays.of(user.getWeeklyCommute().keySet());
    }
}
