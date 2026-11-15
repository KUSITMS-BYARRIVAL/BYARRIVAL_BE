package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.RouteCard;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteCardRepository extends JpaRepository<RouteCard, Long> {

    List<RouteCard> findAllByRouteIdOrderByPoolOrder(Long routeId);

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
}
