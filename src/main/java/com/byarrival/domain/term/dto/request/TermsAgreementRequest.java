package com.byarrival.domain.term.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TermsAgreementRequest(
    @NotEmpty List<@Valid @NotNull Agreement> agreements
) {

    public record Agreement(
        @NotNull Long termsId,
        @NotNull Boolean agreed
    ) {
    }
}
