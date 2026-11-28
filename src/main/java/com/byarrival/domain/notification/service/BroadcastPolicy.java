package com.byarrival.domain.notification.service;

import java.time.LocalDateTime;

/**
 * 어드민 알림 발송 규칙.
 */
public final class BroadcastPolicy {

    /** 야간 광고 제한 시간(21~08시) */
    static final int NIGHT_START_HOUR = 21;
    static final int NIGHT_END_HOUR = 8;

    private BroadcastPolicy() {
    }

    /**
     * 보내는 시각이 야간(21:00~07:59)인지. 야간에는 이벤트(광고)를 야간 광고성 정보 수신 동의자에게만 보낸다.
     */
    public static boolean isNight(LocalDateTime sendAt) {
        int hour = sendAt.getHour();
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }
}
