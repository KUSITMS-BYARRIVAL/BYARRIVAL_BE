package com.byarrival.domain.route.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 노선 추천 결과.
 *
 * @param selected      추천 구성 기사 (선택 순서)
 * @param pool          후보 카드 풀 (추천 구성 + 보충 후보, 섞은 순서)
 * @param fallbackStep  0: 확정, 1: 폴백 1로 최소 시간 충족, 2: 최소 시간 미달로 확정
 * @param candidateHardCount 후보 풀의 HARD 기사 수
 * @param candidateEasyCount 후보 풀의 EASY 기사 수
 */
public record RouteRecommendation(
    List<RouteCandidate> selected,
    List<RouteCandidate> pool,
    int fallbackStep,
    int candidateHardCount,
    int candidateEasyCount
) {

    public RouteRecommendation {
        selected = List.copyOf(selected);
        pool = List.copyOf(pool);
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public int candidateCount() {
        return candidateHardCount + candidateEasyCount;
    }

    public Set<Long> selectedArticleIds() {
        return selected.stream().map(RouteCandidate::articleId).collect(Collectors.toUnmodifiableSet());
    }

    public BigDecimal selectedMinutes() {
        return selected.stream().map(RouteCandidate::readingMinutes).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int selectedCount(ArticleDifficulty difficulty) {
        return (int) selected.stream().filter(candidate -> candidate.difficulty() == difficulty).count();
    }
}
