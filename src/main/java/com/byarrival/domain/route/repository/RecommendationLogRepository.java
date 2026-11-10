package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.RecommendationLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    Optional<RecommendationLog> findByRouteId(Long routeId);
}
