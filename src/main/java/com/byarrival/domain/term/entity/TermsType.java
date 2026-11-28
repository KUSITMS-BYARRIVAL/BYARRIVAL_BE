package com.byarrival.domain.term.entity;

/**
 * 약관 종류
 */
public enum TermsType {
    SERVICE,
    PRIVACY,
    /** 마케팅 정보 수신 동의 (선택) */
    MARKETING,
    /** 야간(21~08시) 광고성 정보 수신 동의 (선택). 마케팅 정보 수신에 동의한 사람만 동의할 수 있다 */
    MARKETING_NIGHT
}
