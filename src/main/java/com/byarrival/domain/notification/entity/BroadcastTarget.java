package com.byarrival.domain.notification.entity;

import com.byarrival.domain.user.entity.TravelType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 알림 대상(NOTI-01). 전체이거나 유형(직장인·학생·기타)별이다.
 */
@Getter
@RequiredArgsConstructor
public enum BroadcastTarget {

    ALL(null),
    WORKER(TravelType.WORKER),
    STUDENT(TravelType.STUDENT),
    ETC(TravelType.ETC);

    /** 이 대상의 회원 유형. 전체면 null */
    private final TravelType travelType;
}
