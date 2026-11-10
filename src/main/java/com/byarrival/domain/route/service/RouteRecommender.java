package com.byarrival.domain.route.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.route.config.RouteProperties;
import com.byarrival.domain.route.config.RouteProperties.DifficultyRatio;
import com.byarrival.domain.route.entity.RouteCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 규칙 기반 노선 추천. 컨디션별 난이도 비율로 목표 시간 T만큼 기사를 고르고, 같은 비율로 보충 후보를 더해 후보 카드 풀을 만든다.
 */
@Component
public class RouteRecommender {

    private static final int FALLBACK_NONE = 0;
    private static final int FALLBACK_FILLED = 1;
    private static final int FALLBACK_SHORT = 2;

    private final RouteProperties properties;
    private final Random random;

    public RouteRecommender(RouteProperties properties, @Qualifier("routeRandom") Random random) {
        this.properties = properties;
        this.random = random;
    }

    public RouteRecommendation recommend(List<RouteCandidate> candidates, int targetMinutes, RouteCondition condition) {
        DifficultyRatio ratio = properties.ratioOf(condition);
        BigDecimal target = BigDecimal.valueOf(targetMinutes);
        BigDecimal min = target.multiply(properties.minTimeRatio());
        BigDecimal max = target.multiply(properties.maxTimeRatio());

        List<RouteCandidate> hardPool = shuffled(filter(candidates, ArticleDifficulty.HARD));
        List<RouteCandidate> easyPool = shuffled(filter(candidates, ArticleDifficulty.EASY));
        int candidateHardCount = hardPool.size();
        int candidateEasyCount = easyPool.size();

        // 부족한 쪽 난이도 풀에서 1건씩 고른다. 그 풀에 더할 기사가 없거나 T에 닿으면 멈춘다
        List<RouteCandidate> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        while (total.compareTo(target) < 0) {
            List<RouteCandidate> pool = deficient(selected, ratio) == ArticleDifficulty.HARD ? hardPool : easyPool;
            RouteCandidate picked = takeFitting(pool, total, max);
            if (picked == null) {
                break;
            }
            selected.add(picked);
            total = total.add(picked.readingMinutes());
        }

        int fallbackStep = FALLBACK_NONE;
        if (total.compareTo(min) < 0) {
            // 폴백 1: 난이도 비율을 풀고 남은 관심 분야 기사로 채운다. 상한은 그대로 둔다
            List<RouteCandidate> rest = new ArrayList<>(hardPool);
            rest.addAll(easyPool);
            Collections.shuffle(rest, random);
            for (RouteCandidate candidate : rest) {
                if (total.compareTo(target) >= 0) {
                    break;
                }
                if (fits(total, candidate, max)) {
                    selected.add(candidate);
                    total = total.add(candidate.readingMinutes());
                    hardPool.remove(candidate);
                    easyPool.remove(candidate);
                }
            }
            // 폴백 2: 그래도 모자라면 고른 기사만으로 확정한다
            fallbackStep = total.compareTo(min) >= 0 ? FALLBACK_FILLED : FALLBACK_SHORT;
        }

        if (selected.isEmpty()) {
            return new RouteRecommendation(List.of(), List.of(), fallbackStep, candidateHardCount, candidateEasyCount);
        }

        List<RouteCandidate> pool = new ArrayList<>(selected);
        pool.addAll(supplements(hardPool, easyPool, ratio, selected.size() * (properties.poolMultiplier() - 1)));
        Collections.shuffle(pool, random);
        return new RouteRecommendation(selected, pool, fallbackStep, candidateHardCount, candidateEasyCount);
    }

    /**
     * 추천 구성과 같은 난이도 비율 규칙으로 남은 기사에서 보충 후보를 고른다. 한쪽 풀이 비면 다른 쪽에서 채운다.
     */
    private List<RouteCandidate> supplements(List<RouteCandidate> hardPool, List<RouteCandidate> easyPool,
        DifficultyRatio ratio, int count) {
        List<RouteCandidate> supplements = new ArrayList<>();
        while (supplements.size() < count && !(hardPool.isEmpty() && easyPool.isEmpty())) {
            List<RouteCandidate> pool = deficient(supplements, ratio) == ArticleDifficulty.HARD ? hardPool : easyPool;
            if (pool.isEmpty()) {
                pool = pool == hardPool ? easyPool : hardPool;
            }
            supplements.add(pool.removeFirst());
        }
        return supplements;
    }

    /**
     * 지금까지 고른 기사의 HARD:EASY가 목표 비율보다 부족한 쪽 난이도. 같으면 랜덤으로 고른다.
     */
    private ArticleDifficulty deficient(List<RouteCandidate> selected, DifficultyRatio ratio) {
        if (ratio.hard() == 0) {
            return ArticleDifficulty.EASY;
        }
        if (ratio.easy() == 0) {
            return ArticleDifficulty.HARD;
        }
        long hard = selected.stream().filter(candidate -> candidate.difficulty() == ArticleDifficulty.HARD).count();
        long easy = selected.size() - hard;
        long hardWeight = hard * ratio.easy();
        long easyWeight = easy * ratio.hard();
        if (hardWeight < easyWeight) {
            return ArticleDifficulty.HARD;
        }
        if (hardWeight > easyWeight) {
            return ArticleDifficulty.EASY;
        }
        return random.nextBoolean() ? ArticleDifficulty.HARD : ArticleDifficulty.EASY;
    }

    /**
     * 섞어 둔 풀에서 더해도 상한을 넘지 않는 첫 기사를 꺼낸다. 넘는 기사는 건너뛴다.
     */
    private RouteCandidate takeFitting(List<RouteCandidate> pool, BigDecimal total, BigDecimal max) {
        Iterator<RouteCandidate> iterator = pool.iterator();
        while (iterator.hasNext()) {
            RouteCandidate candidate = iterator.next();
            if (fits(total, candidate, max)) {
                iterator.remove();
                return candidate;
            }
        }
        return null;
    }

    private boolean fits(BigDecimal total, RouteCandidate candidate, BigDecimal max) {
        return total.add(candidate.readingMinutes()).compareTo(max) <= 0;
    }

    private List<RouteCandidate> filter(List<RouteCandidate> candidates, ArticleDifficulty difficulty) {
        return candidates.stream().filter(candidate -> candidate.difficulty() == difficulty).toList();
    }

    private List<RouteCandidate> shuffled(List<RouteCandidate> candidates) {
        List<RouteCandidate> copy = new ArrayList<>(candidates);
        Collections.shuffle(copy, random);
        return copy;
    }
}
