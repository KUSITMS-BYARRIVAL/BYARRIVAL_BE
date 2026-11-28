package com.byarrival.domain.term.event;

import java.time.LocalDateTime;

/**
 * 광고성 정보 수신 동의가 바뀌었다. 커밋 뒤 동의·철회 결과를 알림함으로 안내한다(정보통신망법).
 *
 * @param marketing 바뀐 광고성 정보 수신 동의. 바뀌지 않았으면 null
 * @param night     바뀐 야간 광고성 정보 수신 동의. 바뀌지 않았으면 null
 */
public record MarketingConsentChangedEvent(Long userId, LocalDateTime changedAt, Boolean marketing, Boolean night) {
}
