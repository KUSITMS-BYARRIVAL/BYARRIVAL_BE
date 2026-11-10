package com.byarrival.domain.route.service;

import com.byarrival.domain.route.config.RouteProperties;
import com.byarrival.domain.route.entity.RouteCard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 담은 카드 합계와 담기·시작하기 가능 여부. 합계는 카드별 소수 읽기시간을 더한 뒤 올림한 값으로 판정한다.
 */
@Component
@RequiredArgsConstructor
public class RouteCartPolicy {

    private final RouteProperties properties;

    public CartStatus evaluate(int targetMinutes, List<RouteCard> cards) {
        BigDecimal target = BigDecimal.valueOf(targetMinutes);
        int minMinutes = target.multiply(properties.minTimeRatio()).setScale(0, RoundingMode.CEILING).intValue();
        int maxMinutes = target.multiply(properties.maxTimeRatio()).setScale(0, RoundingMode.FLOOR).intValue();

        List<RouteCard> carted = cards.stream().filter(RouteCard::isCarted).toList();
        BigDecimal exactMinutes = carted.stream()
            .map(RouteCard::getReadingMinutes)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalMinutes = ceil(exactMinutes);

        List<Long> addableArticleIds = cards.stream()
            .filter(card -> !card.isCarted())
            .filter(card -> ceil(exactMinutes.add(card.getReadingMinutes())) <= maxMinutes)
            .map(RouteCard::getArticleId)
            .toList();
        boolean noMoreCards = addableArticleIds.isEmpty();
        boolean startable = !carted.isEmpty() && totalMinutes <= maxMinutes
            && (totalMinutes >= minMinutes || noMoreCards);

        return new CartStatus(carted.size(), exactMinutes, totalMinutes, minMinutes, maxMinutes, addableArticleIds,
            noMoreCards, startable);
    }

    private static int ceil(BigDecimal minutes) {
        return minutes.setScale(0, RoundingMode.CEILING).intValue();
    }

    /**
     * @param exactMinutes      담은 카드의 소수 읽기시간 합
     * @param totalMinutes      표시·판정에 쓰는 합계(올림)
     * @param minMinutes        시작하기 최소 시간(T×최소 비율, 올림)
     * @param maxMinutes        담을 수 있는 최대 시간(T×최대 비율, 내림)
     * @param addableArticleIds 아직 담지 않았고 담아도 최대 시간을 넘지 않는 카드
     * @param noMoreCards       더 담을 수 있는 카드가 없음
     * @param startable         시작하기 활성 여부
     */
    public record CartStatus(
        int cartCount,
        BigDecimal exactMinutes,
        int totalMinutes,
        int minMinutes,
        int maxMinutes,
        List<Long> addableArticleIds,
        boolean noMoreCards,
        boolean startable
    ) {

        public boolean isAddable(Long articleId) {
            return addableArticleIds.contains(articleId);
        }
    }
}
