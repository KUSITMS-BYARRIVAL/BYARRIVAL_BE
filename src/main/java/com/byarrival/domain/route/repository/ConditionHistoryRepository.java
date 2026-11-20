package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.ConditionHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConditionHistoryRepository extends JpaRepository<ConditionHistory, Long> {

    boolean existsByRouteId(Long routeId);

    Optional<ConditionHistory> findByRouteId(Long routeId);
}
