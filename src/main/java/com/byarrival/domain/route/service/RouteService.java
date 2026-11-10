package com.byarrival.domain.route.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.route.dto.response.BoardingPassResponse;
import com.byarrival.domain.route.dto.response.RouteCardResponse;
import com.byarrival.domain.route.dto.response.RouteCartResponse;
import com.byarrival.domain.route.dto.response.RouteResponse;
import com.byarrival.domain.route.dto.response.TodayRouteResponse;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.exception.RouteErrorCode;
import com.byarrival.domain.route.repository.RouteCardRepository;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.route.service.RouteCartPolicy.CartStatus;
import com.byarrival.domain.user.dto.UserTravelSetting;
import com.byarrival.domain.user.service.UserTravelSettingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 오늘의 노선 조회·생성과 후보 카드 담기·시작하기.
 * 노선 저장과 이력 저장을 서로 다른 트랜잭션으로 나누기 위해 이 클래스는 트랜잭션을 직접 열지 않고 각 서비스에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteCardRepository routeCardRepository;
    private final RouteCommandService routeCommandService;
    private final RouteRecommender routeRecommender;
    private final RouteCartPolicy routeCartPolicy;
    private final RouteHistoryRecorder routeHistoryRecorder;
    private final RouteCursorStore routeCursorStore;
    private final UserTravelSettingService userTravelSettingService;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final Clock clock;

    /**
     * 오늘 서비스일의 노선과 상태. 노선이 없으면 NONE이다.
     */
    public TodayRouteResponse getToday(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return routeRepository.findByUserIdAndServiceDate(userId, ServiceDay.of(now))
            .map(route -> TodayRouteResponse.from(toResponse(route, now)))
            .orElseGet(TodayRouteResponse::none);
    }

    /**
     * 컨디션을 받아 오늘의 노선(후보 카드 풀)을 만든다. 오늘 이미 만든 노선이 있으면 새로 만들지 않고 그 노선을 돌려준다.
     * 추천 결과가 0건이면 노선을 만들지 않고 EMPTY를 돌려준다.
     */
    public TodayRouteResponse create(Long userId, RouteCondition condition) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate serviceDate = ServiceDay.of(now);
        Optional<Route> existing = routeRepository.findByUserIdAndServiceDate(userId, serviceDate);
        if (existing.isPresent()) {
            return TodayRouteResponse.from(toResponse(existing.get(), now));
        }

        UserTravelSetting setting = userTravelSettingService.getTravelSetting(userId);
        Integer targetMinutes = setting.targetMinutes(serviceDate.getDayOfWeek());
        if (targetMinutes == null || targetMinutes <= 0) {
            throw new BusinessException(RouteErrorCode.ROUTE_TRAVEL_TIME_NOT_SET);
        }
        List<RouteCandidate> candidates = articleQueryService
            .findRecommendable(serviceDate, setting.interestCategoryIds()).stream()
            .map(article -> new RouteCandidate(article.getId(), article.getDifficulty(), article.getReadingMinutes()))
            .toList();
        RouteRecommendation recommendation = routeRecommender.recommend(candidates, targetMinutes, condition);

        if (recommendation.isEmpty()) {
            routeHistoryRecorder.recordEmpty(userId, serviceDate, condition, targetMinutes,
                setting.interestCategoryIds(), recommendation);
            return TodayRouteResponse.empty();
        }

        Route route;
        try {
            route = routeCommandService.create(userId, serviceDate, condition, targetMinutes, recommendation);
        } catch (DataIntegrityViolationException e) {
            // 연타나 다른 기기에서 먼저 만든 노선을 돌려준다
            Route first = routeRepository.findByUserIdAndServiceDate(userId, serviceDate).orElseThrow(() -> e);
            return TodayRouteResponse.from(toResponse(first, now));
        }
        routeHistoryRecorder.recordCreated(route, recommendation, setting.interestCategoryIds());
        return TodayRouteResponse.from(toResponse(route, now));
    }

    public RouteResponse addCard(Long userId, Long routeId, Long articleId) {
        LocalDateTime now = LocalDateTime.now(clock);
        routeCommandService.addCard(userId, routeId, articleId, now);
        return toResponse(getRoute(userId, routeId), now);
    }

    public RouteResponse removeCard(Long userId, Long routeId, Long articleId) {
        LocalDateTime now = LocalDateTime.now(clock);
        routeCommandService.removeCard(userId, routeId, articleId, now);
        return toResponse(getRoute(userId, routeId), now);
    }

    /**
     * 시작하기: 담은 카드를 저장하고 보딩패스를 돌려준다. 추천 로그에 담은 카드와 합계 시간을 남긴다.
     */
    public BoardingPassResponse board(Long userId, Long routeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RouteCard> cartCards = routeCommandService.board(userId, routeId, now);
        Route route = getRoute(userId, routeId);
        BigDecimal cartMinutes = cartCards.stream()
            .map(RouteCard::getReadingMinutes)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        routeHistoryRecorder.recordBoarding(routeId, cartCards.stream().map(RouteCard::getArticleId).toList(),
            cartMinutes, now);

        CartStatus cart = routeCartPolicy.evaluate(route.getTargetMinutes(), cartCards);
        List<RouteCardResponse> cards = toCardResponses(cartCards, cart);
        return new BoardingPassResponse(routeId, cards.size(), ceil(cartMinutes), route.getTargetMinutes(), cards);
    }

    public RouteResponse returnToCardSelection(Long userId, Long routeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        routeCommandService.returnToCardSelection(userId, routeId, now);
        return toResponse(getRoute(userId, routeId), now);
    }

    /**
     * 후보 카드 화면에서 마지막으로 본 카드 순번을 남긴다.
     */
    public void saveCursor(Long userId, Long routeId, int cardOrder) {
        LocalDateTime now = LocalDateTime.now(clock);
        Route route = getRoute(userId, routeId);
        if (route.isExpired(now)) {
            throw new BusinessException(RouteErrorCode.ROUTE_EXPIRED);
        }
        if (cardOrder > routeCardRepository.findAllByRouteIdOrderByPoolOrder(routeId).size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        routeCursorStore.save(routeId, cardOrder, now, route.getExpiresAt());
    }

    private Route getRoute(Long userId, Long routeId) {
        return routeRepository.findByIdAndUserId(routeId, userId)
            .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_NOT_FOUND));
    }

    private RouteResponse toResponse(Route route, LocalDateTime now) {
        List<RouteCard> cards = routeCardRepository.findAllByRouteIdOrderByPoolOrder(route.getId());
        CartStatus cart = routeCartPolicy.evaluate(route.getTargetMinutes(), cards);
        List<Long> cartArticleIds = cards.stream()
            .filter(RouteCard::isCarted)
            .sorted(Comparator.comparing(RouteCard::getCartOrder))
            .map(RouteCard::getArticleId)
            .toList();
        RouteCartResponse cartResponse = new RouteCartResponse(cart.cartCount(), cart.totalMinutes(),
            cart.minMinutes(), cart.maxMinutes(), cart.startable(), cart.noMoreCards(), cartArticleIds);
        return new RouteResponse(route.getId(), route.statusAt(now), route.getCondition(), route.getServiceDate(),
            route.getExpiresAt(), route.getTargetMinutes(), route.getFallbackStep(),
            routeCursorStore.find(route.getId()).orElse(null), cartResponse, toCardResponses(cards, cart));
    }

    private List<RouteCardResponse> toCardResponses(List<RouteCard> cards, CartStatus cart) {
        Map<Long, Article> articles = articleQueryService.findAllByIds(
            cards.stream().map(RouteCard::getArticleId).toList());
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        return cards.stream()
            .map(card -> {
                Article article = articles.get(card.getArticleId());
                Long categoryId = article == null ? null : article.getCategoryId();
                return new RouteCardResponse(
                    card.getPoolOrder(),
                    card.getArticleId(),
                    article == null ? null : article.getTitle(),
                    categoryId,
                    categoryId == null ? null : categoryNames.get(categoryId),
                    article == null ? null : article.getPressName(),
                    card.getDifficulty(),
                    ceil(card.getReadingMinutes()),
                    article == null ? null : article.getSummaryOneLine(),
                    card.isRecommended(),
                    card.isCarted(),
                    card.getCartOrder(),
                    cart.isAddable(card.getArticleId()));
            })
            .toList();
    }

    private static int ceil(BigDecimal minutes) {
        return minutes.setScale(0, RoundingMode.CEILING).intValue();
    }
}
