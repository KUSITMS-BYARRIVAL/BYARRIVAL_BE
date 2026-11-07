package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.Article;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("SELECT a.guid FROM Article a WHERE a.guid IN :guids")
    List<String> findExistingGuids(@Param("guids") Collection<String> guids);

    @Query("SELECT a.originalUrl FROM Article a WHERE a.originalUrl IN :originalUrls")
    List<String> findExistingOriginalUrls(@Param("originalUrls") Collection<String> originalUrls);

    @Query("SELECT a.sourceUrl FROM Article a WHERE a.sourceUrl IN :sourceUrls")
    List<String> findExistingSourceUrls(@Param("sourceUrls") Collection<String> sourceUrls);

    // 같은 서비스일에 이미 고른 기사(카테고리가 매핑된 기사). 하루 최대 카테고리 수 × 카테고리별 최대 수만큼이다.
    List<Article> findAllByServiceDateAndCategoryIdIsNotNull(LocalDate serviceDate);

    @Query("SELECT a.categoryId AS categoryId, COUNT(a) AS articleCount FROM Article a "
        + "WHERE a.serviceDate = :serviceDate AND a.categoryId IN :categoryIds GROUP BY a.categoryId")
    List<CategoryArticleCount> countByCategory(@Param("serviceDate") LocalDate serviceDate,
        @Param("categoryIds") Collection<Long> categoryIds);

    interface CategoryArticleCount {

        Long getCategoryId();

        long getArticleCount();
    }
}
