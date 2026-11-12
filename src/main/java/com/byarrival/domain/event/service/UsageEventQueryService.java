package com.byarrival.domain.event.service;

import com.byarrival.domain.event.repository.UsageEventLogRepository;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나의 기록(STAT-02)이 쓰는 이용 이벤트 조회
 */
@Service
@RequiredArgsConstructor
public class UsageEventQueryService {

    private final UsageEventLogRepository repository;

    /**
     * @return 기간 안에 앱에 접속한 날짜(KST 자정 기준). 이벤트 로그는 6개월 보관이므로 그 전 날짜는 없다
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> findAppAccessDates(Long userId, LocalDate from, LocalDate to) {
        return Set.copyOf(repository.findAppAccessDates(userId, from, to));
    }
}
