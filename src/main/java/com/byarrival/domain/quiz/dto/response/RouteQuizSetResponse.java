package com.byarrival.domain.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RouteQuizSetResponse(
    Long routeId,
    @Schema(description = "이어서 풀 문제 순서. 모두 풀었으면 null") Integer nextQuizOrder,
    List<RouteQuizResponse> quizzes,
    QuizProgressResponse progress
) {
}
