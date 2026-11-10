package com.byarrival.domain.route.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.exception.RouteErrorCode;
import com.byarrival.domain.route.repository.RouteCardRepository;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.route.service.RouteCartPolicy.CartStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선 저장과 담기·빼기·시작하기 상태 변경. 상태 변경은 노선 행을 잠그고 처리한다.
 */
@Service
@RequiredArgsConstructor
public class RouteCommandService {

    private final RouteRepository routeRepository;
    private final RouteCardRepository routeCardRepository;
    private final RouteCartPolicy routeCartPolicy;

    /**
     * 노선과 후보 카드 풀 스냅샷을 저장한다. 같은 서비스일에 먼저 만든 노선이 있으면 유니크 제약 위반으로 실패한다.
     */
    @Transactional
    public Route create(Long userId, LocalDate serviceDate, RouteCondition condition, int targetMinutes,
        RouteRecommendation recommendation) {
        Route route = routeRepository.saveAndFlush(Route.builder()
            .userId(userId)
            .serviceDate(serviceDate)
            .condition(condition)
            .targetMinutes(targetMinutes)
            .fallbackStep(recommendation.fallbackStep())
            .expiresAt(ServiceDay.endOf(serviceDate))
            .build());

        List<RouteCard> cards = new ArrayList<>();
        List<RouteCandidate> pool = recommendation.pool();
        for (int i = 0; i < pool.size(); i++) {
            RouteCandidate candidate = pool.get(i);
            cards.add(RouteCard.builder()
                .routeId(route.getId())
                .articleId(candidate.articleId())
                .poolOrder(i + 1)
                .difficulty(candidate.difficulty())
                .readingMinutes(candidate.readingMinutes())
                .recommended(recommendation.selectedArticleIds().contains(candidate.articleId()))
                .build());
        }
        routeCardRepository.saveAll(cards);
        return route;
    }

    /**
     * 카드를 담는다. 이미 담은 카드면 그대로 둔다. 담으면 최대 시간을 넘는 카드는 담지 않는다.
     */
    @Transactional
    public void addCard(Long userId, Long routeId, Long articleId, LocalDateTime now) {
        Route route = lockSelectable(userId, routeId, now);
        List<RouteCard> cards = routeCardRepository.findAllByRouteIdOrderByPoolOrder(route.getId());
        RouteCard card = findCard(cards, articleId);
        if (card.isCarted()) {
            return;
        }
        CartStatus cart = routeCartPolicy.evaluate(route.getTargetMinutes(), cards);
        if (!cart.isAddable(articleId)) {
            throw new BusinessException(RouteErrorCode.ROUTE_CART_OVER_LIMIT);
        }
        int nextOrder = cards.stream()
            .map(RouteCard::getCartOrder)
            .filter(order -> order != null)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0) + 1;
        card.addToCart(nextOrder, now);
    }

    /**
     * 카드를 뺀다. 담지 않은 카드면 그대로 둔다.
     */
    @Transactional
    public void removeCard(Long userId, Long routeId, Long articleId, LocalDateTime now) {
        Route route = lockSelectable(userId, routeId, now);
        RouteCard card = findCard(routeCardRepository.findAllByRouteIdOrderByPoolOrder(route.getId()), articleId);
        if (card.isCarted()) {
            card.removeFromCart();
        }
    }

    /**
     * 시작하기: 담은 카드가 시작 조건을 만족하면 보딩패스 단계로 넘긴다. 이미 보딩패스 단계면 그대로 둔다.
     *
     * @return 담은 카드 (담은 순서)
     */
    @Transactional
    public List<RouteCard> board(Long userId, Long routeId, LocalDateTime now) {
        Route route = lockActive(userId, routeId, now);
        List<RouteCard> cards = routeCardRepository.findAllByRouteIdOrderByPoolOrder(route.getId());
        if (route.getStatus() == RouteStatus.CREATED) {
            CartStatus cart = routeCartPolicy.evaluate(route.getTargetMinutes(), cards);
            if (!cart.startable()) {
                throw new BusinessException(RouteErrorCode.ROUTE_NOT_STARTABLE);
            }
            route.board(now);
        } else if (route.getStatus() != RouteStatus.BOARDING) {
            throw new BusinessException(RouteErrorCode.ROUTE_INVALID_STATUS);
        }
        return cards.stream()
            .filter(RouteCard::isCarted)
            .sorted((a, b) -> Integer.compare(a.getCartOrder(), b.getCartOrder()))
            .toList();
    }

    /**
     * 보딩패스에서 뒤로 가기: 담은 카드는 그대로 두고 카드 선택 단계로 돌아간다. 이미 카드 선택 단계면 그대로 둔다.
     */
    @Transactional
    public void returnToCardSelection(Long userId, Long routeId, LocalDateTime now) {
        Route route = lockActive(userId, routeId, now);
        if (route.getStatus() == RouteStatus.BOARDING) {
            route.returnToCardSelection();
        } else if (route.getStatus() != RouteStatus.CREATED) {
            throw new BusinessException(RouteErrorCode.ROUTE_INVALID_STATUS);
        }
    }

    private Route lockSelectable(Long userId, Long routeId, LocalDateTime now) {
        Route route = lockActive(userId, routeId, now);
        if (route.getStatus() != RouteStatus.CREATED) {
            throw new BusinessException(RouteErrorCode.ROUTE_INVALID_STATUS);
        }
        return route;
    }

    private Route lockActive(Long userId, Long routeId, LocalDateTime now) {
        Route route = routeRepository.findForUpdate(routeId, userId)
            .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_NOT_FOUND));
        if (route.isExpired(now)) {
            throw new BusinessException(RouteErrorCode.ROUTE_EXPIRED);
        }
        return route;
    }

    private RouteCard findCard(List<RouteCard> cards, Long articleId) {
        return cards.stream()
            .filter(card -> card.getArticleId().equals(articleId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_CARD_NOT_IN_POOL));
    }
}
