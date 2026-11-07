package com.byarrival.domain.term.dto.response;

import com.byarrival.domain.term.entity.UserTermsAgreement;
import java.time.LocalDateTime;

public record TermsAgreementResponse(
    Long termsId,
    boolean agreed,
    LocalDateTime agreedAt
) {

    public static TermsAgreementResponse from(UserTermsAgreement agreement) {
        return new TermsAgreementResponse(agreement.getTermsId(), agreement.isAgreed(), agreement.getAgreedAt());
    }
}
