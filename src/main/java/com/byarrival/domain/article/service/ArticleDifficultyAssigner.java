package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 카테고리 안에서 난이도 점수가 높은 절반을 HARD, 나머지를 EASY로 배정한다(2026-11-08 결정).
 * 홀수면 HARD가 1건 많다. 배치를 다시 돌릴 때는 이미 요약한 기사의 난이도까지 합쳐 반반을 맞춘다.
 */
@Component
public class ArticleDifficultyAssigner {

    /**
     * @param scores       새로 배정할 기사의 점수
     * @param existingHard 같은 카테고리에서 이미 HARD로 요약한 기사 수
     * @param existingEasy 같은 카테고리에서 이미 EASY로 요약한 기사 수
     */
    public Map<Long, ArticleDifficulty> assign(Map<Long, Integer> scores, long existingHard, long existingEasy) {
        long total = existingHard + existingEasy + scores.size();
        long targetHard = (total + 1) / 2;
        long hardSlots = Math.clamp(targetHard - existingHard, 0, scores.size());

        // 점수가 같으면 먼저 저장된(Feed 순서가 앞선) 기사를 HARD로 둔다
        List<Long> ranked = scores.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .toList();
        Map<Long, ArticleDifficulty> assigned = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            assigned.put(ranked.get(i), i < hardSlots ? ArticleDifficulty.HARD : ArticleDifficulty.EASY);
        }
        return assigned;
    }
}
