package com.byarrival.domain.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record QuizAnswerRequest(
    @Schema(description = "고른 선택지 번호(1부터)") @NotNull Integer choiceSeq
) {
}
