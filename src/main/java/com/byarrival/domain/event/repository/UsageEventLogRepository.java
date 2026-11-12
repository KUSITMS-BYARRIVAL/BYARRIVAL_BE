package com.byarrival.domain.event.repository;

import com.byarrival.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 이용 이벤트 로그(LOG-01) 저장과 파기. 같은 이벤트는 유니크 인덱스로 한 번만 남는다.
 */
public interface UsageEventLogRepository extends Repository<User, Long> {

    @Modifying
    @Query(value = "INSERT INTO usage_event_log (user_id, route_id, article_id, event_type, service_date, occurred_at) "
        + "VALUES (:userId, :routeId, :articleId, 'ARTICLE_READ', :serviceDate, :occurredAt) "
        + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertArticleRead(@Param("userId") Long userId, @Param("routeId") Long routeId,
        @Param("articleId") Long articleId, @Param("serviceDate") LocalDate serviceDate,
        @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying
    @Query(value = "INSERT INTO usage_event_log (user_id, route_id, event_type, service_date, occurred_at) "
        + "VALUES (:userId, :routeId, 'ROUTE_COMPLETED', :serviceDate, :occurredAt) "
        + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertRouteCompleted(@Param("userId") Long userId, @Param("routeId") Long routeId,
        @Param("serviceDate") LocalDate serviceDate, @Param("occurredAt") LocalDateTime occurredAt);

    @Modifying
    @Query(value = "DELETE FROM usage_event_log WHERE occurred_at < :before", nativeQuery = true)
    int deleteOccurredBefore(@Param("before") LocalDateTime before);
}
