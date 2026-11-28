package com.byarrival.domain.term.event;

import java.time.LocalDateTime;

/**
 * 광고성 정보 수신 동의 후 2년이 지나 동의 여부를 다시 안내한다(정보통신망법).
 *
 * @param agreedAt 마지막으로 동의한 시각
 */
public record MarketingConsentReconfirmEvent(Long userId, LocalDateTime agreedAt) {
}
