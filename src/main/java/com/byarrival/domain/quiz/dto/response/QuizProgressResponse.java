package com.byarrival.domain.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "퀴즈 결과 집계(QUIZ-03). 안 푼 문제는 다시 들어오면 이어서 푼다")
public record QuizProgressResponse(
    @Schema(description = "전체 문제 수(최대 3). 0이면 출제할 문제가 없어 결과 화면으로 간다") int total,
    int answeredCount,
    int correctCount,
    @Schema(description = "모두 풀었는지") boolean finished
) {
}
