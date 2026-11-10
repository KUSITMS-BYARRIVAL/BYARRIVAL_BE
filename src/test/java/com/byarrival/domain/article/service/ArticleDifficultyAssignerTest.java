package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArticleDifficultyAssignerTest {

    private final ArticleDifficultyAssigner assigner = new ArticleDifficultyAssigner();

    @Test
    void 점수가_높은_절반을_HARD로_나머지를_EASY로_배정한다() {
        Map<Long, ArticleDifficulty> assigned = assigner.assign(
            Map.of(1L, 3, 2L, 9, 3L, 5, 4L, 7), 0, 0);

        assertThat(assigned).containsExactlyInAnyOrderEntriesOf(Map.of(
            2L, ArticleDifficulty.HARD, 4L, ArticleDifficulty.HARD,
            3L, ArticleDifficulty.EASY, 1L, ArticleDifficulty.EASY));
    }

    @Test
    void LLM_점수가_한쪽으로_몰려도_반반으로_나눈다() {
        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (long id = 1; id <= 20; id++) {
            scores.put(id, id <= 13 ? 9 : 2);
        }

        Map<Long, ArticleDifficulty> assigned = assigner.assign(scores, 0, 0);

        assertThat(assigned.values()).filteredOn(ArticleDifficulty.HARD::equals).hasSize(10);
        assertThat(assigned.values()).filteredOn(ArticleDifficulty.EASY::equals).hasSize(10);
    }

    @Test
    void 홀수면_HARD가_1건_많고_점수가_같으면_먼저_저장된_기사를_HARD로_둔다() {
        Map<Long, ArticleDifficulty> assigned = assigner.assign(Map.of(5L, 4, 3L, 4, 9L, 4), 0, 0);

        assertThat(assigned).containsExactlyInAnyOrderEntriesOf(Map.of(
            3L, ArticleDifficulty.HARD, 5L, ArticleDifficulty.HARD, 9L, ArticleDifficulty.EASY));
    }

    @Test
    void 다시_실행할_때는_이미_요약한_기사의_난이도까지_합쳐_반반을_맞춘다() {
        // 이미 HARD 6건·EASY 2건이 있고 새로 4건 → 전체 12건이므로 HARD는 6건이면 된다
        Map<Long, ArticleDifficulty> assigned = assigner.assign(Map.of(1L, 9, 2L, 8, 3L, 2, 4L, 1), 6, 2);

        assertThat(assigned.values()).containsOnly(ArticleDifficulty.EASY);
    }
}
