package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.Route;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);

    Optional<Route> findByIdAndUserId(Long id, Long userId);

    // 담기·시작하기를 노선 단위로 줄 세워 합계 검사와 담은 순서가 엇갈리지 않게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id AND r.userId = :userId")
    Optional<Route> findForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
