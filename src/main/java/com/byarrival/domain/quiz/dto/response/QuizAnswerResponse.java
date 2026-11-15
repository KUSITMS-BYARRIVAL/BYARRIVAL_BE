package com.byarrival.domain.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuizAnswerResponse(
    Long quizId,
    @Schema(description = "저장된 답. 이미 답한 문제를 다시 보내면 처음 답을 그대로 돌려준다") int selectedSeq,
    int answerSeq,
    boolean correct,
    String explanation,
    QuizProgressResponse progress
) {
}
