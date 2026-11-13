package com.byarrival.domain.quiz.service;

import com.byarrival.domain.quiz.entity.RouteQuiz;
import com.byarrival.domain.quiz.repository.RouteQuizRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선 퀴즈를 한 트랜잭션으로 고정한다. 처음 여는 요청이 겹쳐 유니크 제약에 걸리면 먼저 고정된 문제를 쓴다.
 */
@Component
@RequiredArgsConstructor
class RouteQuizWriter {

    private final RouteQuizRepository routeQuizRepository;

    @Transactional
    public void fix(Long routeId, List<Long> quizIds, LocalDateTime now) {
        for (int i = 0; i < quizIds.size(); i++) {
            routeQuizRepository.save(RouteQuiz.create(routeId, quizIds.get(i), i + 1, now));
        }
        routeQuizRepository.flush();
    }
}
