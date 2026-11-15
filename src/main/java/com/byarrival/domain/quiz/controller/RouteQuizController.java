package com.byarrival.domain.quiz.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.quiz.dto.request.QuizAnswerRequest;
import com.byarrival.domain.quiz.dto.response.QuizAnswerResponse;
import com.byarrival.domain.quiz.dto.response.QuizProgressResponse;
import com.byarrival.domain.quiz.dto.response.RouteQuizSetResponse;
import com.byarrival.domain.quiz.service.RouteQuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quiz", description = "오늘의 퀴즈")
@RestController
@RequestMapping("/api/v1/routes/{routeId}/quizzes")
@RequiredArgsConstructor
public class RouteQuizController {

    private final RouteQuizService routeQuizService;

    @Operation(summary = "오늘의 퀴즈 조회",
        description = "도착(완주)한 오늘 노선에서 완독한 기사로 3지선다 퀴즈를 최대 3문제 낸다. 처음 열 때 문제를 골라 고정하고, "
            + "다시 들어오면 nextQuizOrder부터 이어서 푼다. progress.total이 0이면 출제할 문제가 없어 결과 화면으로 간다. "
            + "도착 전이면 ROUTE_INVALID_STATUS, 04:00이 지나면 ROUTE_EXPIRED.")
    @GetMapping
    public ApiResponse<RouteQuizSetResponse> getQuizzes(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeQuizService.getQuizzes(userId, routeId));
    }

    @Operation(summary = "퀴즈 답 제출",
        description = "정오답과 정답·해설을 돌려준다. 문제당 처음 낸 답만 저장하며, 다시 보내면 처음 답의 결과를 그대로 돌려준다.")
    @PostMapping("/{quizId}/answers")
    public ApiResponse<QuizAnswerResponse> answer(@LoginUserId Long userId, @PathVariable Long routeId,
        @PathVariable Long quizId, @Valid @RequestBody QuizAnswerRequest request) {
        return ApiResponse.ok(routeQuizService.answer(userId, routeId, quizId, request.choiceSeq()));
    }

    @Operation(summary = "퀴즈 결과", description = "전체 문제 수와 맞힌 수. 안 푼 문제가 있으면 finished=false.")
    @GetMapping("/result")
    public ApiResponse<QuizProgressResponse> getResult(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeQuizService.getResult(userId, routeId));
    }
}
