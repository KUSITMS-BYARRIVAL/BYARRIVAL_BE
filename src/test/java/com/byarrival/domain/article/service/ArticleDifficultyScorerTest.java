package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleDifficultyProperties;
import com.byarrival.domain.article.dto.ArticleDifficultyOutput;
import com.byarrival.domain.article.dto.ArticleDifficultyOutput.Score;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.DifficultyScoringException;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.ScoreInput;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.ScoreResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArticleDifficultyScorerTest {

    private static final List<ScoreInput> ARTICLES = List.of(
        new ScoreInput(1L, "국고채 금리 상승", "가".repeat(1000)),
        new ScoreInput(2L, "빵값 인상", "짧은 본문"));

    private final ClaudeClient claudeClient = mock(ClaudeClient.class);
    private final ArticleDifficultyScorer scorer = new ArticleDifficultyScorer(claudeClient,
        new ArticleDifficultyProperties("claude-haiku-5-5", 4000, "low", 400, 3));

    @Test
    void 카테고리_기사를_한_번에_보내_기사별_점수를_받는다() {
        given(claudeClient.generate(any(), eq(ArticleDifficultyOutput.class)))
            .willReturn(result(new Score(1L, 8), new Score(2L, 2)));

        ScoreResult result = scorer.score(ARTICLES);

        assertThat(result.scores()).isEqualTo(Map.of(1L, 8, 2L, 2));
        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.usage()).isEqualTo(new TokenUsage(500, 100));
    }

    @Test
    void 토큰을_아끼려고_본문은_앞부분만_보낸다() {
        given(claudeClient.generate(any(), eq(ArticleDifficultyOutput.class)))
            .willReturn(result(new Score(1L, 8), new Score(2L, 2)));

        scorer.score(ARTICLES);

        ArgumentCaptor<ClaudeRequest> request = ArgumentCaptor.forClass(ClaudeRequest.class);
        verify(claudeClient).generate(request.capture(), eq(ArticleDifficultyOutput.class));
        assertThat(request.getValue().userMessage())
            .contains("<lead>" + "가".repeat(400) + "</lead>")
            .doesNotContain("가".repeat(401))
            .contains("<article id=\"2\">");
    }

    @Test
    void 점수가_빠지거나_범위를_벗어나면_다시_요청한다() {
        given(claudeClient.generate(any(), eq(ArticleDifficultyOutput.class)))
            .willReturn(result(new Score(1L, 8)))
            .willReturn(result(new Score(1L, 8), new Score(2L, 11)))
            .willReturn(result(new Score(1L, 8), new Score(2L, 3)));

        ScoreResult result = scorer.score(ARTICLES);

        assertThat(result.scores()).isEqualTo(Map.of(1L, 8, 2L, 3));
        assertThat(result.calls()).isEqualTo(3);
        assertThat(result.usage()).isEqualTo(new TokenUsage(1500, 300));
    }

    @Test
    void 다시_요청해도_통과하지_못하면_쓴_토큰과_함께_실패한다() {
        given(claudeClient.generate(any(), eq(ArticleDifficultyOutput.class)))
            .willReturn(result(new Score(1L, 8), new Score(3L, 2)));

        assertThatThrownBy(() -> scorer.score(ARTICLES))
            .isInstanceOfSatisfying(DifficultyScoringException.class, e -> {
                assertThat(e.calls()).isEqualTo(4);
                assertThat(e.usage()).isEqualTo(new TokenUsage(2000, 400));
            });
        verify(claudeClient, times(4)).generate(any(), eq(ArticleDifficultyOutput.class));
    }

    private static ClaudeResult<ArticleDifficultyOutput> result(Score... scores) {
        return new ClaudeResult<>(new ArticleDifficultyOutput(List.of(scores)), new TokenUsage(500, 100));
    }
}
