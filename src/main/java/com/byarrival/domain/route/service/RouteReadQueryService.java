package com.byarrival.domain.route.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.exception.RouteErrorCode;
import com.byarrival.domain.route.repository.RouteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인(퀴즈)이 쓰는 노선 완독 기록 조회
 */
@Service
@RequiredArgsConstructor
public class RouteReadQueryService {

    private final RouteRepository routeRepository;
    private final RideProgressReader rideProgressReader;
    private final Clock clock;

    /**
     * 오늘 완주한 내 노선에서 완독한 기사 ID를 정거장 순서대로 돌려준다. 퀴즈는 도착한 뒤에 풀고, 만료된 노선은 볼 수 없다.
     */
    @Transactional(readOnly = true)
    public List<Long> findReadArticleIdsOfCompleted(Long userId, Long routeId) {
        Route route = routeRepository.findByIdAndUserId(routeId, userId)
            .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_NOT_FOUND));
        if (route.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(RouteErrorCode.ROUTE_EXPIRED);
        }
        if (route.getStatus() != RouteStatus.COMPLETED) {
            throw new BusinessException(RouteErrorCode.ROUTE_INVALID_STATUS);
        }
        return rideProgressReader.read(route).stops().stream()
            .filter(RouteCard::isRead)
            .map(RouteCard::getArticleId)
            .toList();
    }
}
