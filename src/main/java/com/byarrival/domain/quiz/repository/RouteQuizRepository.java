package com.byarrival.domain.quiz.repository;

import com.byarrival.domain.quiz.entity.RouteQuiz;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteQuizRepository extends JpaRepository<RouteQuiz, Long> {

    List<RouteQuiz> findAllByRouteIdOrderByQuizOrder(Long routeId);

    Optional<RouteQuiz> findByRouteIdAndQuizId(Long routeId, Long quizId);

    // 아직 답하지 않은 문제에만 답을 저장한다. 연타로 두 요청이 겹쳐도 처음 답 하나만 남는다
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteQuiz rq SET rq.selectedSeq = :selectedSeq, rq.correct = :correct, rq.answeredAt = :now "
        + "WHERE rq.id = :id AND rq.answeredAt IS NULL")
    int answerIfUnanswered(@Param("id") Long id, @Param("selectedSeq") int selectedSeq,
        @Param("correct") boolean correct, @Param("now") LocalDateTime now);
}
