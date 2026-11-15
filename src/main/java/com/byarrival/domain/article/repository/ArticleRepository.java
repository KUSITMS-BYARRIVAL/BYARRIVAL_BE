package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 같은 사건 정리 대상: 그 서비스일의 추천 후보 중 숨기지 않은 기사. 먼저 고른(먼저 저장한) 기사부터 읽는다.
    // 숨긴 기사가 대표로 남아 공개 기사가 빠지지 않게 한다. visibility는 엔티티에 매핑하지 않아 네이티브 쿼리로 거른다
    @Query(value = "SELECT * FROM article WHERE service_date = :serviceDate AND recommendable "
        + "AND visibility = 'VISIBLE' ORDER BY id", nativeQuery = true)
    List<Article> findStoryCandidates(@Param("serviceDate") LocalDate serviceDate);

    // 추천에서 뺀 기사를 가리키던 중복 기록을 새로 남긴 기사로 옮긴다(배치 재실행으로 남길 기사가 바뀐 경우)
    @Modifying
    @Query("UPDATE Article a SET a.duplicateOfArticleId = :representativeId WHERE a.duplicateOfArticleId = :removedId")
    int repointDuplicates(@Param("removedId") Long removedId, @Param("representativeId") Long representativeId);

    // 숨겼거나 원문이 삭제된 기사. 노선 운행에서 건너뛴다(READ-01)
    @Query(value = "SELECT id FROM article WHERE id IN (:articleIds) AND visibility <> 'VISIBLE'", nativeQuery = true)
    List<Long> findUnavailableIds(@Param("articleIds") Collection<Long> articleIds);

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
