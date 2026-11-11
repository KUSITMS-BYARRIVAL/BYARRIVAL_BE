package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
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

    @Query("SELECT a.categoryId AS categoryId, a.difficulty AS difficulty, COUNT(a) AS articleCount FROM Article a "
        + "WHERE a.serviceDate = :serviceDate AND a.processStatus = :status AND a.categoryId IS NOT NULL "
        + "GROUP BY a.categoryId, a.difficulty")
    List<CategoryDifficultyCount> countDifficultyByCategory(@Param("serviceDate") LocalDate serviceDate,
        @Param("status") ArticleProcessStatus status);

    @Query("SELECT a.categoryId AS categoryId, COUNT(a) AS articleCount FROM Article a "
        + "WHERE a.serviceDate = :serviceDate AND a.recommendable = true AND a.categoryId IN :categoryIds "
        + "GROUP BY a.categoryId")
    List<CategoryArticleCount> countRecommendableByCategory(@Param("serviceDate") LocalDate serviceDate,
        @Param("categoryIds") Collection<Long> categoryIds);

    // 노선 추천 후보 풀: 그 서비스일의 추천 가능 기사 중 관심 분야에 속하고 숨기지 않은 기사.
    // visibility는 아직 엔티티에 매핑하지 않아 네이티브 쿼리로 거른다(ix_article_recommend 인덱스 조건과 같다)
    @Query(value = "SELECT * FROM article WHERE service_date = :serviceDate AND recommendable "
        + "AND visibility = 'VISIBLE' AND category_id IN (:categoryIds)", nativeQuery = true)
    List<Article> findRecommendable(@Param("serviceDate") LocalDate serviceDate,
        @Param("categoryIds") Collection<Long> categoryIds);

    // 같은 사건 정리 대상: 그 서비스일의 추천 후보. 먼저 고른(먼저 저장한) 기사부터 읽는다
    List<Article> findAllByServiceDateAndRecommendableTrueOrderById(LocalDate serviceDate);

    interface CategoryDifficultyCount {

        Long getCategoryId();

        ArticleDifficulty getDifficulty();

        long getArticleCount();
    }

    interface CategoryArticleCount {

        Long getCategoryId();

        long getArticleCount();
    }
}
