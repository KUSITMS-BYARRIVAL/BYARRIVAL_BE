package com.byarrival.domain.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RouteQuizResponse(
    @Schema(description = "문제 순서(1부터)") int quizOrder,
    Long quizId,
    Long categoryId,
    String categoryName,
    Long articleId,
    @Schema(description = "문제 아래 작은 글씨로 보여 줄 뉴스 제목") String articleTitle,
    String question,
    List<QuizChoiceResponse> choices,
    boolean answered,
    @Schema(description = "고른 선택지. 안 풀었으면 null") Integer selectedSeq,
    @Schema(description = "맞혔는지. 안 풀었으면 null") Boolean correct,
    @Schema(description = "정답 선택지. 안 풀었으면 null") Integer answerSeq,
    @Schema(description = "해설. 안 풀었으면 null") String explanation
) {
}
