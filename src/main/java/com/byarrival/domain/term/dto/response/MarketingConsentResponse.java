package com.byarrival.domain.term.dto.response;

import java.time.LocalDateTime;

/**
 * 내 광고성 정보 수신 동의. 동의하거나 철회한 적이 없으면 동의하지 않은 것(false)이다.
 *
 * @param marketing 광고성 정보 수신
 * @param night     야간(21~08시) 광고성 정보 수신
 */
public record MarketingConsentResponse(Consent marketing, Consent night) {

    /**
     * @param changedAt 마지막으로 동의하거나 철회한 시각. 한 번도 없으면 null
     */
    public record Consent(boolean agreed, LocalDateTime changedAt) {
    }
}
