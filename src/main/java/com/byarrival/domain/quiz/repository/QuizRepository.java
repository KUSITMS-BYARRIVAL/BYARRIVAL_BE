package com.byarrival.domain.quiz.repository;

import com.byarrival.domain.quiz.entity.Quiz;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @Query("SELECT q FROM Quiz q WHERE q.articleId IN :articleIds AND q.status = "
        + "com.byarrival.domain.quiz.entity.QuizStatus.ACTIVE")
    List<Quiz> findActiveByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    @Query("SELECT q.articleId FROM Quiz q WHERE q.articleId IN :articleIds")
    List<Long> findArticleIdsWithQuiz(@Param("articleIds") Collection<Long> articleIds);
}
