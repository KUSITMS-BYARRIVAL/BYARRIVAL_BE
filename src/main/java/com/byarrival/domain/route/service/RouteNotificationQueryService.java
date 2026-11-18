package com.byarrival.domain.route.service;

import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.repository.IdleRoute;
import com.byarrival.domain.route.repository.RouteCardRepository;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.user.entity.JobGroup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 알림이 노선 상태와 담기·읽기 기록을 확인할 때 쓰는 조회.
 */
@Service
@RequiredArgsConstructor
public class RouteNotificationQueryService {

    private final RouteRepository routeRepository;
    private final RouteCardRepository routeCardRepository;

    @Transactional(readOnly = true)
    public Optional<Route> findRoute(Long userId, LocalDate serviceDate) {
        return routeRepository.findByUserIdAndServiceDate(userId, serviceDate);
    }

    /**
     * 완주하지 않았고 만료 전인 노선 중 마지막 활동 시각이 from~to(양 끝 포함)인 것
     */
    @Transactional(readOnly = true)
    public List<IdleRoute> findIdleRoutes(LocalDate serviceDate, LocalDateTime now, LocalDateTime from,
        LocalDateTime to) {
        return routeRepository.findIdleRoutes(serviceDate, now, from, to);
    }

    /**
     * 그 서비스일에 담은 사용자가 많은 기사 순. 기사마다 담은 사용자가 minUsers 이상인 것만 넣는다.
     * 근무 지역과 직군 중 null인 쪽은 조건에서 뺀다. 둘 다 null이면 비어 있다.
     */
    @Transactional(readOnly = true)
    public List<CartedArticleCount> findPopular(LocalDate serviceDate, Integer sigunguId, JobGroup jobGroup,
        int minUsers, int limit) {
        if (sigunguId != null && jobGroup != null) {
            return routeCardRepository.findPopularInRegionAndJobGroup(serviceDate, sigunguId, jobGroup.name(),
                minUsers, limit);
        }
        if (jobGroup != null) {
            return routeCardRepository.findPopularInJobGroup(serviceDate, jobGroup.name(), minUsers, limit);
        }
        if (sigunguId != null) {
            return routeCardRepository.findPopularInRegion(serviceDate, sigunguId, minUsers, limit);
        }
        return List.of();
    }

    /**
     * 그 사용자가 [기사 보러가기]로 읽은 기사 중 articleIds에 든 것
     */
    @Transactional(readOnly = true)
    public Set<Long> findReadArticleIds(Long userId, Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(routeCardRepository.findReadArticleIds(userId, articleIds));
    }
}
