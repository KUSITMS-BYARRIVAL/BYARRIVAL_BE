package com.byarrival.domain.route.repository;

import com.byarrival.domain.route.entity.RouteCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteCardRepository extends JpaRepository<RouteCard, Long> {

    List<RouteCard> findAllByRouteIdOrderByPoolOrder(Long routeId);
}
