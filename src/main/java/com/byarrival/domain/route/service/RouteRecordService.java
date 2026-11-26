package com.byarrival.domain.route.service;

import com.byarrival.domain.route.repository.DailyRideRecord;
import com.byarrival.domain.route.repository.RouteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나의 기록(STAT)이 쓰는 노선 이용 기록 조회. 완독·완주는 노선의 서비스일에 귀속한다(ARR-01).
 */
@Service
@RequiredArgsConstructor
public class RouteRecordService {

    private final RouteRepository routeRepository;

    /**
     * 가장 최근에 출발한(탑승한) 시각. 출발한 노선이 없으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findLastStartedAt(Long userId) {
        return Optional.ofNullable(routeRepository.findLastStartedAt(userId));
    }

    @Transactional(readOnly = true)
    public List<DailyRideRecord> findDailyRecords(Long userId, LocalDate from, LocalDate to) {
        return routeRepository.findDailyRecords(userId, from, to);
    }
}
