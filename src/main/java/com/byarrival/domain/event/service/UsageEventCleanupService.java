package com.byarrival.domain.event.service;

import com.byarrival.domain.event.repository.UsageEventLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간(6개월)이 지난 이용 이벤트 로그를 파기한다(LOG-01). 기준은 이벤트 발생 시각이다.
 */
@Service
@RequiredArgsConstructor
public class UsageEventCleanupService {

    static final Period RETENTION = Period.ofMonths(6);

    private final UsageEventLogRepository usageEventLogRepository;
    private final Clock clock;

    @Transactional
    public int deleteExpired() {
        return usageEventLogRepository.deleteOccurredBefore(LocalDateTime.now(clock).minus(RETENTION));
    }
}
