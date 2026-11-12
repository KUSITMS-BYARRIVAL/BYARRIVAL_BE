package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.Route;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

    // 담기·시작하기를 노선 단위로 줄 세워 합계 검사와 담은 순서가 엇갈리지 않게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id AND r.userId = :userId")
    Optional<Route> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
