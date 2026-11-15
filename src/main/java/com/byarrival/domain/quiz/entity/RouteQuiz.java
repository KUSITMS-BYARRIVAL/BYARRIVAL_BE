package com.byarrival.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노선에 고정한 퀴즈 한 문제와 사용자 응답(QUIZ-01~03). 응답은 한 번만 저장한다(RouteQuizRepository.answerIfUnanswered).
 */
@Entity
@Table(name = "route_quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private Long quizId;

    // 1부터 시작하는 문제 순서
    @Column(nullable = false)
    private int quizOrder;

    private Integer selectedSeq;

    private Boolean correct;

    private LocalDateTime answeredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private RouteQuiz(Long routeId, Long quizId, int quizOrder, LocalDateTime now) {
        this.routeId = routeId;
        this.quizId = quizId;
        this.quizOrder = quizOrder;
        this.createdAt = now;
    }

    public static RouteQuiz create(Long routeId, Long quizId, int quizOrder, LocalDateTime now) {
        return new RouteQuiz(routeId, quizId, quizOrder, now);
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }
}
