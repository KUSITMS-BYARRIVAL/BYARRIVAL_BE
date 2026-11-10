package com.byarrival.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.route.config.RouteProperties;
import com.byarrival.domain.route.config.RouteProperties.DifficultyRatio;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.service.RouteCartPolicy.CartStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteCartPolicyTest {

    private static final RouteProperties PROPERTIES = new RouteProperties(new BigDecimal("0.7"),
        new BigDecimal("1.2"), 2, Map.of(
        RouteCondition.SUNNY, new DifficultyRatio(3, 2),
        RouteCondition.NORMAL, new DifficultyRatio(2, 3),
        RouteCondition.CLOUDY, new DifficultyRatio(1, 4),
        RouteCondition.RAINY, new DifficultyRatio(1, 4)));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 8, 0);

    private final RouteCartPolicy policy = new RouteCartPolicy(PROPERTIES);

    @Test
    void 합계는_소수_읽기시간을_더한_뒤_올림한다() {
        List<RouteCard> cards = List.of(carted(1L, "1.2", 1), carted(2L, "1.2", 2), card(3L, "1.0"));

        CartStatus status = policy.evaluate(10, cards);

        assertThat(status.exactMinutes()).isEqualByComparingTo("2.4");
        assertThat(status.totalMinutes()).isEqualTo(3);
        assertThat(status.cartCount()).isEqualTo(2);
    }

    @Test
    void 합계가_T의_70에서_120퍼센트_사이면_시작할_수_있다() {
        CartStatus belowMin = policy.evaluate(10, List.of(carted(1L, "6.0", 1), card(2L, "1.0")));
        CartStatus inRange = policy.evaluate(10, List.of(carted(1L, "7.0", 1), card(2L, "1.0")));

        assertThat(belowMin.minMinutes()).isEqualTo(7);
        assertThat(belowMin.maxMinutes()).isEqualTo(12);
        assertThat(belowMin.startable()).isFalse();
        assertThat(inRange.startable()).isTrue();
    }

    @Test
    void 담으면_최대_시간을_넘는_카드는_담을_수_없다() {
        CartStatus status = policy.evaluate(10, List.of(carted(1L, "10.0", 1), card(2L, "2.0"), card(3L, "2.1")));

        assertThat(status.isAddable(2L)).isTrue();
        assertThat(status.isAddable(3L)).isFalse();
    }

    @Test
    void 더_담을_카드가_없으면_최소_시간_미만이어도_시작할_수_있다() {
        CartStatus status = policy.evaluate(30, List.of(carted(1L, "3.0", 1), carted(2L, "3.0", 2)));

        assertThat(status.noMoreCards()).isTrue();
        assertThat(status.startable()).isTrue();
    }

    @Test
    void 담은_카드가_없으면_시작할_수_없다() {
        CartStatus status = policy.evaluate(10, List.of(card(1L, "30.0")));

        assertThat(status.noMoreCards()).isTrue();
        assertThat(status.startable()).isFalse();
    }

    private RouteCard card(Long articleId, String minutes) {
        return RouteCard.builder()
            .routeId(1L)
            .articleId(articleId)
            .poolOrder(articleId.intValue())
            .difficulty(ArticleDifficulty.EASY)
            .readingMinutes(new BigDecimal(minutes))
            .recommended(true)
            .build();
    }

    private RouteCard carted(Long articleId, String minutes, int cartOrder) {
        RouteCard card = card(articleId, minutes);
        card.addToCart(cartOrder, NOW);
        return card;
    }
}
