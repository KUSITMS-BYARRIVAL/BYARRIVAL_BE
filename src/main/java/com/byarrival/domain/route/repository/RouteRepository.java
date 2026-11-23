package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.Route;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);

    Optional<Route> findByIdAndUserId(Long id, Long userId);

    // 스트릭 계산용: 완주한 서비스일 (최근부터)
    @Query("SELECT r.serviceDate FROM Route r WHERE r.userId = :userId AND r.status = "
        + "com.byarrival.domain.route.entity.RouteStatus.COMPLETED AND r.serviceDate <= :today "
        + "ORDER BY r.serviceDate DESC")
    List<LocalDate> findCompletedServiceDates(@Param("userId") Long userId, @Param("today") LocalDate today);

    // 여러 회원의 스트릭을 한 번에 계산할 때 쓴다. 회원별 최근 완주일부터 온다
    @Query("SELECT r.userId AS userId, r.serviceDate AS serviceDate FROM Route r WHERE r.userId IN :userIds "
        + "AND r.status = com.byarrival.domain.route.entity.RouteStatus.COMPLETED AND r.serviceDate <= :today "
        + "ORDER BY r.userId, r.serviceDate DESC")
    List<UserCompletedDate> findCompletedServiceDatesOf(@Param("userIds") Collection<Long> userIds,
        @Param("today") LocalDate today);

    // 서비스일별 노선 이용 기록. 서비스일당 노선은 하나다
    @Query(value = "SELECT r.service_date AS serviceDate, COUNT(rc.read_at) AS readCount, "
        + "r.status = 'COMPLETED' AS completed, r.route_condition AS condition, "
        + "CASE WHEN r.status = 'COMPLETED' AND r.started_at IS NOT NULL "
        + "THEN CAST(EXTRACT(EPOCH FROM (r.completed_at - r.started_at)) AS BIGINT) ELSE 0 END AS usedSeconds "
        + "FROM route r LEFT JOIN route_card rc ON rc.route_id = r.id "
        + "WHERE r.user_id = :userId AND r.service_date BETWEEN :fromDate AND :toDate "
        + "GROUP BY r.id ORDER BY r.service_date", nativeQuery = true)
    List<DailyRideRecord> findDailyRecords(@Param("userId") Long userId, @Param("fromDate") LocalDate from,
        @Param("toDate") LocalDate to);

    // 담기·시작하기를 노선 단위로 줄 세워 합계 검사와 담은 순서가 엇갈리지 않게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id AND r.userId = :userId")
    Optional<Route> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 완주하지 않은 오늘 노선 중 마지막 활동(노선 생성·보딩·출발·기사 보러가기 중 가장 늦은 시각)이 from~to(양 끝 포함)인 것.
     * 남은 정거장 수는 담은 기사 중 아직 읽지 않은 수다.
     */
    @Query(value = "SELECT id AS routeId, user_id AS userId, remaining_stops AS remainingStops FROM ("
        + "SELECT r.id, r.user_id, "
        + "GREATEST(r.created_at, r.boarded_at, r.started_at, "
        + "(SELECT MAX(rc.read_at) FROM route_card rc WHERE rc.route_id = r.id)) AS last_activity_at, "
        + "(SELECT COUNT(*) FROM route_card rc WHERE rc.route_id = r.id AND rc.cart_order IS NOT NULL "
        + "AND rc.read_at IS NULL) AS remaining_stops "
        + "FROM route r WHERE r.service_date = :serviceDate AND r.status <> 'COMPLETED' AND r.expires_at > :now"
        + ") idle WHERE last_activity_at BETWEEN :from AND :to ORDER BY id", nativeQuery = true)
    List<IdleRoute> findIdleRoutes(@Param("serviceDate") LocalDate serviceDate, @Param("now") LocalDateTime now,
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    interface UserCompletedDate {

        Long getUserId();

        LocalDate getServiceDate();
    }
}
