package com.byarrival.domain.route.service;

import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.repository.RouteCardRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 노선의 운행 진행 상태를 읽는다. 담은 카드와, 그중 숨겼거나 원문이 삭제된 기사를 함께 본다.
 */
@Component
@RequiredArgsConstructor
public class RideProgressReader {

    private final RouteCardRepository routeCardRepository;
    private final ArticleQueryService articleQueryService;

    public RideProgress read(Route route) {
        return read(routeCardRepository.findAllByRouteIdOrderByPoolOrder(route.getId()));
    }

    public RideProgress read(List<RouteCard> cards) {
        return RideProgress.of(cards, articleQueryService.findUnavailableIds(cards.stream()
            .filter(RouteCard::isCarted)
            .map(RouteCard::getArticleId)
            .toList()));
    }
}
