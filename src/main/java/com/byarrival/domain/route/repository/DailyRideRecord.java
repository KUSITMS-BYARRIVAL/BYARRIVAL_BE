package com.byarrival.domain.route.repository;

import java.time.LocalDate;

/**
 * 서비스일 하나의 노선 이용 기록(STAT-01~03)
 */
public interface DailyRideRecord {

    LocalDate getServiceDate();

    // [기사 보러가기]를 누른(완독한) 기사 수
    long getReadCount();

    boolean getCompleted();

    // 완주한 노선의 이용시간(출발~도착, 초). 완주하지 않았으면 0
    long getUsedSeconds();
}
