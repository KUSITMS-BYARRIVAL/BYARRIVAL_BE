package com.byarrival.domain.admin.service;

import com.byarrival.domain.admin.repository.AdminMemberAccessLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간(2년)이 지난 회원 열람 기록을 파기한다(MEMBER-04). 기준은 열람 시각이다.
 * 개인정보 안전성 확보조치 기준(접속기록 1년 이상, 조건에 따라 2년 이상 보관)을 넉넉히 따른다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberAccessLogCleanupService {

    static final Period RETENTION = Period.ofYears(2);

    private final AdminMemberAccessLogRepository accessLogRepository;
    private final Clock clock;

    @Transactional
    public int deleteExpired() {
        return accessLogRepository.deleteAccessedBefore(LocalDateTime.now(clock).minus(RETENTION));
    }
}
