package com.byarrival.domain.term.dto.response;

import com.byarrival.domain.term.entity.Terms;
import com.byarrival.domain.term.entity.TermsType;

public record TermsResponse(
    Long termsId,
    TermsType type,
    String title,
    String version,
    String contentUrl,
    boolean required
) {

    public static TermsResponse from(Terms terms) {
        return new TermsResponse(terms.getId(), terms.getTermsType(), terms.getTitle(), terms.getVersion(),
            terms.getContentUrl(), terms.isRequired());
    }
}
