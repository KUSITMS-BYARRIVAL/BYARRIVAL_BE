package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.ai.config.AnthropicProperties;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.service.ArticleAiService.AiProcessResult;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.DifficultyScoringException;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.ScoreResult;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryResult;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryTarget;
import com.byarrival.domain.article.service.ArticleSummaryService.UsageTotal;
import com.byarrival.domain.article.service.ArticleSummaryStore.PendingArticle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArticleAiServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 11);

    private final ArticleSummaryStore store = mock(ArticleSummaryStore.class);
    private final ArticleDifficultyScorer scorer = mock(ArticleDifficultyScorer.class);
    private final ArticleSummaryService summaryService = mock(ArticleSummaryService.class);
    private final BatchAlertNotifier notifier = mock(BatchAlertNotifier.class);
    private ArticleAiService service;

    @BeforeEach
    void setUp() {
        AnthropicProperties anthropic = new AnthropicProperties("key", Duration.ofSeconds(60), 2, 0.10, 0.50);
        service = new ArticleAiService(store, scorer, new ArticleDifficultyAssigner(), summaryService, anthropic,
            notifier, Clock.fixed(Instant.parse("2026-11-10T14:45:00Z"), ZoneId.of("Asia/Seoul")));
        given(store.countSummarizedDifficulty(SERVICE_DATE)).willReturn(List.of());
    }

    @Test
    void 카테고리마다_난이도를_판정해_반반으로_배정한_뒤_요약한다() {
        given(store.loadPending(SERVICE_DATE)).willReturn(List.of(
            pending(1L, 10L), pending(2L, 10L), pending(3L, 20L), pending(4L, 20L)));
        given(scorer.score(any())).willAnswer(invocation -> {
            List<ArticleDifficultyScorer.ScoreInput> inputs = invocation.getArgument(0);
            long first = inputs.getFirst().articleId();
            return new ScoreResult(Map.of(first, 2, first + 1, 9), 1, new TokenUsage(1000, 100));
        });
        given(summaryService.summarize(any())).willReturn(summary(List.of()));

        service.process(SERVICE_DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SummaryTarget>> targets = ArgumentCaptor.forClass(List.class);
        verify(summaryService).summarize(targets.capture());
        assertThat(targets.getValue()).containsExactlyInAnyOrder(
            new SummaryTarget(2L, ArticleDifficulty.HARD), new SummaryTarget(1L, ArticleDifficulty.EASY),
            new SummaryTarget(4L, ArticleDifficulty.HARD), new SummaryTarget(3L, ArticleDifficulty.EASY));
    }

    @Test
    void 난이도_판정에_실패한_카테고리는_요약하지_않고_나머지는_계속한다() {
        given(store.loadPending(SERVICE_DATE)).willReturn(List.of(pending(1L, 10L), pending(2L, 20L)));
        given(scorer.score(any()))
            .willThrow(new DifficultyScoringException(null, 4, new TokenUsage(4000, 400)))
            .willReturn(new ScoreResult(Map.of(2L, 5), 1, new TokenUsage(1000, 100)));
        given(summaryService.summarize(any())).willReturn(summary(List.of()));

        AiProcessResult result = service.process(SERVICE_DATE);

        assertThat(result.failedCategoryIds()).containsExactly(10L);
        assertThat(result.difficultyCalls()).isEqualTo(5);
        verify(summaryService).summarize(List.of(new SummaryTarget(2L, ArticleDifficulty.HARD)));
    }

    @Test
    void 끝나면_난이도_판정과_요약의_호출_토큰_예상_비용을_한_번_알린다() {
        given(store.loadPending(SERVICE_DATE)).willReturn(List.of(pending(1L, 10L), pending(2L, 10L)));
        given(scorer.score(any())).willReturn(new ScoreResult(Map.of(1L, 3, 2L, 7), 1, new TokenUsage(2000, 500)));
        given(summaryService.summarize(any())).willReturn(summary(List.of(1L)));

        service.process(SERVICE_DATE);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(content.capture());
        // 입력 2,000+8,000, 출력 500+3,000 → 0.001 + 0.00175 = 0.00275
        assertThat(content.getValue())
            .contains("AI 가공 완료 (실패 있음)")
            .contains("서비스일 2026-11-11")
            .contains("난이도 판정: 카테고리 1개 · 호출 1회 · 토큰 입력 2,000 / 출력 500")
            .contains("요약: 대상 2건 · 성공 1 · 실패 1 · 호출 3회 (다시 요청 1회) · 토큰 입력 8,000 / 출력 3,000")
            .contains("예상 비용 $0.0028")
            .contains("요약 실패 articleId: [1]");
    }

    @Test
    void 가공할_기사가_없으면_호출하지_않고_알리지도_않는다() {
        given(store.loadPending(SERVICE_DATE)).willReturn(List.of());

        service.process(SERVICE_DATE);

        verify(scorer, never()).score(any());
        verify(summaryService, never()).summarize(any());
        verify(notifier, never()).send(any());
    }

    private static PendingArticle pending(Long articleId, Long categoryId) {
        return new PendingArticle(articleId, categoryId, "제목" + articleId, "본문");
    }

    private static SummaryResult summary(List<Long> failed) {
        return new SummaryResult(2 - failed.size(), failed, 0, new UsageTotal(3, 1, 8000, 3000));
    }
}
