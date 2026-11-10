package com.byarrival.domain.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.route.config.RouteProperties;
import com.byarrival.domain.route.config.RouteProperties.DifficultyRatio;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteCondition;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RouteRecommenderTest {

    private static final RouteProperties PROPERTIES = new RouteProperties(new BigDecimal("0.7"),
        new BigDecimal("1.2"), 2, Map.of(
        RouteCondition.SUNNY, new DifficultyRatio(3, 2),
        RouteCondition.NORMAL, new DifficultyRatio(2, 3),
        RouteCondition.CLOUDY, new DifficultyRatio(1, 4),
        RouteCondition.RAINY, new DifficultyRatio(1, 4)));

    private long nextId = 1;

    @RepeatedTest(20)
    void 컨디션별_난이도_비율로_목표_시간까지_고른다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random());
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "1.0", 20));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "1.0", 20));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.SUNNY);

        assertThat(result.selected()).hasSize(10);
        assertThat(result.selectedCount(ArticleDifficulty.HARD)).isBetween(5, 7);
        assertThat(result.selectedMinutes()).isEqualByComparingTo("10");
        assertThat(result.fallbackStep()).isZero();
        assertThat(result.candidateHardCount()).isEqualTo(20);
        assertThat(result.candidateEasyCount()).isEqualTo(20);
    }

    @RepeatedTest(20)
    void 흐림은_EASY를_더_많이_고른다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random());
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "1.0", 20));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "1.0", 20));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.CLOUDY);

        assertThat(result.selectedCount(ArticleDifficulty.HARD)).isBetween(1, 3);
    }

    @Test
    void 더하면_최대_시간을_넘는_기사는_건너뛴다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(1));
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "13.0", 3));
        candidates.addAll(candidates(ArticleDifficulty.HARD, "2.0", 5));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "2.0", 5));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.SUNNY);

        assertThat(result.selected()).allMatch(candidate -> candidate.readingMinutes().compareTo(new BigDecimal("13")) < 0);
        assertThat(result.selectedMinutes()).isBetween(new BigDecimal("7"), new BigDecimal("12"));
    }

    @RepeatedTest(10)
    void 한쪽_난이도가_모자라면_폴백_1로_비율을_풀고_채운다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random());
        List<RouteCandidate> candidates = candidates(ArticleDifficulty.EASY, "2.0", 10);

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.SUNNY);

        assertThat(result.fallbackStep()).isEqualTo(1);
        assertThat(result.selectedMinutes()).isEqualByComparingTo("10");
    }

    @Test
    void 관심_분야_기사를_다_써도_모자라면_폴백_2로_있는_기사만_확정한다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(1));
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "2.0", 1));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "2.0", 1));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.NORMAL);

        assertThat(result.fallbackStep()).isEqualTo(2);
        assertThat(result.selected()).hasSize(2);
        assertThat(result.pool()).hasSize(2);
    }

    @Test
    void 후보가_없으면_빈_결과를_돌려준다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(1));

        RouteRecommendation result = recommender.recommend(List.of(), 10, RouteCondition.SUNNY);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.pool()).isEmpty();
        assertThat(result.fallbackStep()).isEqualTo(2);
    }

    @Test
    void 후보_카드_풀은_추천_구성의_배수이고_추천_구성을_모두_포함한다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(7));
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "2.0", 20));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "2.0", 20));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.SUNNY);

        assertThat(result.selected()).hasSize(5);
        assertThat(result.pool()).hasSize(10).containsAll(result.selected()).doesNotHaveDuplicates();
        List<RouteCandidate> supplements = result.pool().stream()
            .filter(candidate -> !result.selected().contains(candidate))
            .toList();
        long supplementHard = supplements.stream().filter(c -> c.difficulty() == ArticleDifficulty.HARD).count();
        assertThat(supplementHard).isBetween(2L, 4L);
    }

    @Test
    void 보충할_기사가_모자라면_있는_만큼만_더한다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(3));
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "2.0", 3));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "2.0", 3));

        RouteRecommendation result = recommender.recommend(candidates, 10, RouteCondition.SUNNY);

        assertThat(result.selected()).hasSize(5);
        assertThat(result.pool()).hasSize(6);
    }

    @Test
    void T의_120퍼센트가_정수가_아니면_내림한_상한까지만_고른다() {
        // T=11: 상한 13.2를 내림한 13분. 6.55분 두 건(13.1분, 올림 14분)은 함께 담을 수 없다
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random(1));
        List<RouteCandidate> candidates = candidates(ArticleDifficulty.EASY, "6.55", 2);

        RouteRecommendation result = recommender.recommend(candidates, 11, RouteCondition.RAINY);

        assertThat(result.selected()).hasSize(1);
        assertThat(result.fallbackStep()).isEqualTo(2);
    }

    @RepeatedTest(20)
    void 추천_구성은_담기_정책으로도_모두_담고_시작할_수_있다() {
        RouteRecommender recommender = new RouteRecommender(PROPERTIES, new Random());
        RouteCartPolicy policy = new RouteCartPolicy(PROPERTIES);
        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(candidates(ArticleDifficulty.HARD, "2.35", 10));
        candidates.addAll(candidates(ArticleDifficulty.EASY, "1.85", 10));

        RouteRecommendation result = recommender.recommend(candidates, 11, RouteCondition.SUNNY);

        List<RouteCard> cards = new ArrayList<>();
        int order = 1;
        for (RouteCandidate candidate : result.selected()) {
            RouteCard card = RouteCard.builder()
                .routeId(1L).articleId(candidate.articleId()).poolOrder(order).difficulty(candidate.difficulty())
                .readingMinutes(candidate.readingMinutes()).recommended(true).build();
            card.addToCart(order++, LocalDateTime.of(2026, 11, 10, 8, 0));
            cards.add(card);
        }
        RouteCartPolicy.CartStatus cart = policy.evaluate(11, cards);
        assertThat(cart.totalMinutes()).isLessThanOrEqualTo(cart.maxMinutes());
        assertThat(cart.startable()).isTrue();
    }

    private List<RouteCandidate> candidates(ArticleDifficulty difficulty, String minutes, int count) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candidates.add(new RouteCandidate(nextId++, difficulty, new BigDecimal(minutes)));
        }
        return candidates;
    }
}
