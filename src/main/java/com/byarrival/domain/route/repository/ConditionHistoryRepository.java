package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.ConditionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConditionHistoryRepository extends JpaRepository<ConditionHistory, Long> {

    boolean existsByRouteId(Long routeId);
}
