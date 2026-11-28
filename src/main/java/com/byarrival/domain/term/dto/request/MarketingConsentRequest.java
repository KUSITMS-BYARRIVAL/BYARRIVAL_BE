package com.byarrival.domain.term.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 광고성 정보 수신 동의 변경. 바꾸지 않을 값도 지금 값 그대로 보낸다.
 *
 * @param marketing 광고성 정보 수신 동의
 * @param night     야간(21~08시) 광고성 정보 수신 동의. 광고성 정보 수신에 동의해야 true로 할 수 있다
 */
public record MarketingConsentRequest(
    @NotNull Boolean marketing,
    @NotNull Boolean night
) {
}
