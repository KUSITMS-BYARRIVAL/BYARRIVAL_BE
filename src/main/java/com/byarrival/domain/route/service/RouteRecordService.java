package com.byarrival.domain.route.service;

import com.byarrival.domain.route.repository.DailyRideRecord;
import com.byarrival.domain.route.repository.RouteRepository;
import java.time.LocalDate;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<DailyRideRecord> findDailyRecords(Long userId, LocalDate from, LocalDate to) {
        return routeRepository.findDailyRecords(userId, from, to);
    }
}
