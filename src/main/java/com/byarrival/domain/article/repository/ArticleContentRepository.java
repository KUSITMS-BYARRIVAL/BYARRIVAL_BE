package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleContentRepository extends JpaRepository<ArticleContent, Long> {

    @Query("SELECT c FROM ArticleContent c JOIN FETCH c.article a "
        + "WHERE c.articleId IN :articleIds AND a.processStatus = :status")
    List<ArticleContent> findAllWithArticle(@Param("articleIds") Collection<Long> articleIds,
        @Param("status") ArticleProcessStatus status);

    @Query("SELECT c FROM ArticleContent c JOIN FETCH c.article a "
        + "WHERE a.serviceDate = :serviceDate AND a.processStatus = :status AND a.categoryId IS NOT NULL "
        + "ORDER BY a.id")
    List<ArticleContent> findAllPending(@Param("serviceDate") LocalDate serviceDate,
        @Param("status") ArticleProcessStatus status);
}
