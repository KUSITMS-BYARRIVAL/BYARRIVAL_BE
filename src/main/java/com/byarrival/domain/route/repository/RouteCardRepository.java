package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.RouteCard;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteCardRepository extends JpaRepository<RouteCard, Long> {

    List<RouteCard> findAllByRouteIdOrderByPoolOrder(Long routeId);

    /**
     * 노선의 후보 카드 풀을 지운다. 새 풀을 같은 트랜잭션에서 넣기 전에 바로 지워 유니크 제약과 엇갈리지 않게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RouteCard c WHERE c.routeId = :routeId")
    void deleteAllByRouteId(@Param("routeId") Long routeId);

    /**
     * 그 서비스일에 노선에서 기사를 1건 이상 담은 사용자 수. 담았다가 뺀 카드는 세지 않는다.
     */
    @Query(value = "SELECT COUNT(DISTINCT r.user_id) FROM route_card rc JOIN route r ON r.id = rc.route_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL", nativeQuery = true)
    long countCartedUsers(@Param("serviceDate") LocalDate serviceDate);

    /**
     * 그 서비스일에 담은 사용자가 많은 기사 순. 동률이면 최신 기사가 먼저다. 숨겼거나 삭제한 기사는 뺀다.
     */
    @Query(value = "SELECT rc.article_id AS articleId, COUNT(DISTINCT r.user_id) AS userCount "
        + "FROM route_card rc JOIN route r ON r.id = rc.route_id JOIN article a ON a.id = rc.article_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND a.visibility = 'VISIBLE' "
        + "GROUP BY rc.article_id, a.published_at "
        + "ORDER BY userCount DESC, a.published_at DESC NULLS LAST, rc.article_id DESC "
        + "LIMIT :limit", nativeQuery = true)
    List<CartedArticleCount> findMostCarted(@Param("serviceDate") LocalDate serviceDate, @Param("limit") int limit);

    /**
     * countCartedUsers와 같되, 그 직군을 고른 사용자만 센다(HOME-05 직군 칩). 직군을 2개 고른 사용자는 두 직군에 각각 들어간다.
     */
    @Query(value = "SELECT COUNT(DISTINCT r.user_id) FROM route_card rc JOIN route r ON r.id = rc.route_id "
        + "JOIN user_job_group ujg ON ujg.user_id = r.user_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND ujg.job_group = :jobGroup",
        nativeQuery = true)
    long countCartedUsersInJobGroup(@Param("serviceDate") LocalDate serviceDate, @Param("jobGroup") String jobGroup);

    /**
     * findMostCarted와 같되, 그 직군을 고른 사용자가 담은 것만 센다.
     */
    @Query(value = "SELECT rc.article_id AS articleId, COUNT(DISTINCT r.user_id) AS userCount "
        + "FROM route_card rc JOIN route r ON r.id = rc.route_id JOIN article a ON a.id = rc.article_id "
        + "JOIN user_job_group ujg ON ujg.user_id = r.user_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND a.visibility = 'VISIBLE' "
        + "AND ujg.job_group = :jobGroup "
        + "GROUP BY rc.article_id, a.published_at "
        + "ORDER BY userCount DESC, a.published_at DESC NULLS LAST, rc.article_id DESC "
        + "LIMIT :limit", nativeQuery = true)
    List<CartedArticleCount> findMostCartedInJobGroup(@Param("serviceDate") LocalDate serviceDate,
        @Param("jobGroup") String jobGroup, @Param("limit") int limit);

    /**
     * 그 서비스일에 같은 근무 지역·직군 사용자가 많이 담은 기사. 기사마다 담은 사용자가 minUsers 이상인 것만 순위에 넣는다(인기 기사 알림).
     */
    @Query(value = "SELECT rc.article_id AS articleId, COUNT(DISTINCT r.user_id) AS userCount FROM route_card rc JOIN route r ON r.id = rc.route_id JOIN article a ON a.id = rc.article_id "
        + "JOIN users u ON u.id = r.user_id JOIN user_job_group ujg ON ujg.user_id = r.user_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND a.visibility = 'VISIBLE' "
        + "AND u.region_sigungu_id = :sigunguId AND ujg.job_group = :jobGroup "
        + "GROUP BY rc.article_id, a.published_at HAVING COUNT(DISTINCT r.user_id) >= :minUsers "
        + "ORDER BY userCount DESC, a.published_at DESC NULLS LAST, rc.article_id DESC "
        + "LIMIT :limit", nativeQuery = true)
    List<CartedArticleCount> findPopularInRegionAndJobGroup(@Param("serviceDate") LocalDate serviceDate,
        @Param("sigunguId") Integer sigunguId, @Param("jobGroup") String jobGroup, @Param("minUsers") int minUsers,
        @Param("limit") int limit);

    /**
     * findPopularInRegionAndJobGroup과 같되 직군만 본다.
     */
    @Query(value = "SELECT rc.article_id AS articleId, COUNT(DISTINCT r.user_id) AS userCount FROM route_card rc JOIN route r ON r.id = rc.route_id JOIN article a ON a.id = rc.article_id "
        + "JOIN user_job_group ujg ON ujg.user_id = r.user_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND a.visibility = 'VISIBLE' "
        + "AND ujg.job_group = :jobGroup "
        + "GROUP BY rc.article_id, a.published_at HAVING COUNT(DISTINCT r.user_id) >= :minUsers "
        + "ORDER BY userCount DESC, a.published_at DESC NULLS LAST, rc.article_id DESC "
        + "LIMIT :limit", nativeQuery = true)
    List<CartedArticleCount> findPopularInJobGroup(@Param("serviceDate") LocalDate serviceDate,
        @Param("jobGroup") String jobGroup, @Param("minUsers") int minUsers, @Param("limit") int limit);

    /**
     * findPopularInRegionAndJobGroup과 같되 근무 지역만 본다.
     */
    @Query(value = "SELECT rc.article_id AS articleId, COUNT(DISTINCT r.user_id) AS userCount FROM route_card rc JOIN route r ON r.id = rc.route_id JOIN article a ON a.id = rc.article_id "
        + "JOIN users u ON u.id = r.user_id "
        + "WHERE r.service_date = :serviceDate AND rc.cart_order IS NOT NULL AND a.visibility = 'VISIBLE' "
        + "AND u.region_sigungu_id = :sigunguId "
        + "GROUP BY rc.article_id, a.published_at HAVING COUNT(DISTINCT r.user_id) >= :minUsers "
        + "ORDER BY userCount DESC, a.published_at DESC NULLS LAST, rc.article_id DESC "
        + "LIMIT :limit", nativeQuery = true)
    List<CartedArticleCount> findPopularInRegion(@Param("serviceDate") LocalDate serviceDate,
        @Param("sigunguId") Integer sigunguId, @Param("minUsers") int minUsers, @Param("limit") int limit);

    /**
     * 그 사용자가 [기사 보러가기]로 읽은 기사 중 articleIds에 든 것. 날짜와 상관없이 본다.
     */
    @Query(value = "SELECT DISTINCT rc.article_id FROM route_card rc JOIN route r ON r.id = rc.route_id "
        + "WHERE r.user_id = :userId AND rc.read_at IS NOT NULL AND rc.article_id IN (:articleIds)", nativeQuery = true)
    List<Long> findReadArticleIds(@Param("userId") Long userId, @Param("articleIds") Collection<Long> articleIds);
}
