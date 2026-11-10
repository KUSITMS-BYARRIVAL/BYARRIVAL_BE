package com.byarrival.domain.article.service;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.ai.config.AnthropicProperties;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.repository.ArticleRepository.CategoryDifficultyCount;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.DifficultyScoringException;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.ScoreInput;
import com.byarrival.domain.article.service.ArticleDifficultyScorer.ScoreResult;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryResult;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryTarget;
import com.byarrival.domain.article.service.ArticleSummaryStore.PendingArticle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 서비스일에 정제된 기사를 AI로 가공한다. 카테고리마다 난이도를 판정해 HARD·EASY 반반으로 배정한 뒤 3줄 요약을 만든다.
 * 끝나면 호출 수·토큰·예상 비용을 디스코드 배치 채널에 한 번 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAiService {

    private static final int MAX_FAILED_IDS_IN_ALERT = 30;

    private final ArticleSummaryStore store;
    private final ArticleDifficultyScorer difficultyScorer;
    private final ArticleDifficultyAssigner difficultyAssigner;
    private final ArticleSummaryService summaryService;
    private final AnthropicProperties anthropicProperties;
    private final BatchAlertNotifier batchAlertNotifier;
    private final Clock clock;

    public AiProcessResult process(LocalDate serviceDate) {
        Instant startedAt = clock.instant();
        List<PendingArticle> pending = store.loadPending(serviceDate);
        if (pending.isEmpty()) {
            log.info("[AI 가공] serviceDate={} 가공할 기사 없음", serviceDate);
            return AiProcessResult.empty();
        }

        Map<Long, List<PendingArticle>> byCategory = new LinkedHashMap<>();
        pending.forEach(article -> byCategory.computeIfAbsent(article.categoryId(), id -> new ArrayList<>())
            .add(article));
        Map<Long, long[]> existing = existingDifficultyCounts(serviceDate);

        List<SummaryTarget> targets = new ArrayList<>();
        List<Long> failedCategoryIds = new ArrayList<>();
        long difficultyCalls = 0;
        long difficultyInput = 0;
        long difficultyOutput = 0;
        for (Map.Entry<Long, List<PendingArticle>> entry : byCategory.entrySet()) {
            Long categoryId = entry.getKey();
            List<ScoreInput> inputs = entry.getValue().stream()
                .map(article -> new ScoreInput(article.articleId(), article.title(), article.content()))
                .toList();
            try {
                ScoreResult scored = difficultyScorer.score(inputs);
                difficultyCalls += scored.calls();
                difficultyInput += scored.usage().inputTokens();
                difficultyOutput += scored.usage().outputTokens();
                long[] counts = existing.getOrDefault(categoryId, new long[2]);
                difficultyAssigner.assign(scored.scores(), counts[0], counts[1])
                    .forEach((articleId, difficulty) -> targets.add(new SummaryTarget(articleId, difficulty)));
            } catch (DifficultyScoringException e) {
                // 이 카테고리는 이번 실행에서 요약하지 않는다. 배치를 다시 돌리면 다시 판정한다
                difficultyCalls += e.calls();
                difficultyInput += e.usage().inputTokens();
                difficultyOutput += e.usage().outputTokens();
                failedCategoryIds.add(categoryId);
                log.warn("[AI 가공] serviceDate={} categoryId={} 난이도 판정 실패 - {}",
                    serviceDate, categoryId, e.getMessage());
            }
        }

        SummaryResult summary = summaryService.summarize(targets);
        AiProcessResult result = new AiProcessResult(pending.size(), byCategory.size(), failedCategoryIds,
            difficultyCalls, new TokenUsage(difficultyInput, difficultyOutput), summary);
        notifyCompleted(serviceDate, result, Duration.between(startedAt, clock.instant()));
        return result;
    }

    // 카테고리별 [HARD 수, EASY 수]
    private Map<Long, long[]> existingDifficultyCounts(LocalDate serviceDate) {
        Map<Long, long[]> counts = new HashMap<>();
        for (CategoryDifficultyCount count : store.countSummarizedDifficulty(serviceDate)) {
            if (count.getDifficulty() == null) {
                continue;
            }
            long[] pair = counts.computeIfAbsent(count.getCategoryId(), id -> new long[2]);
            pair[count.getDifficulty() == ArticleDifficulty.HARD ? 0 : 1] += count.getArticleCount();
        }
        return counts;
    }

    // 알림이 실패해도 가공 결과에는 영향을 주지 않는다
    private void notifyCompleted(LocalDate serviceDate, AiProcessResult result, Duration duration) {
        try {
            SummaryResult summary = result.summary();
            boolean hasFailure = !summary.failedArticleIds().isEmpty() || !result.failedCategoryIds().isEmpty();
            long inputTokens = result.difficultyUsage().inputTokens() + summary.usage().inputTokens();
            long outputTokens = result.difficultyUsage().outputTokens() + summary.usage().outputTokens();
            StringBuilder content = new StringBuilder()
                .append(hasFailure ? "⚠️ **AI 가공 완료 (실패 있음)**" : "🤖 **AI 가공 완료**")
                .append(" 서비스일 ").append(serviceDate)
                .append(" · ").append(duration.toSeconds()).append("초\n")
                .append("난이도 판정: 카테고리 ").append(result.categoryCount()).append("개")
                .append(" · 호출 ").append(result.difficultyCalls()).append("회")
                .append(" · 토큰 ").append(tokens(result.difficultyUsage().inputTokens(),
                    result.difficultyUsage().outputTokens())).append('\n')
                .append("요약: 대상 ").append(result.pendingCount()).append("건 · 성공 ").append(summary.succeeded())
                .append(" · 실패 ").append(summary.failedArticleIds().size())
                .append(" · 호출 ").append(summary.usage().calls()).append("회 (다시 요청 ")
                .append(summary.usage().retries()).append("회)")
                .append(" · 토큰 ").append(tokens(summary.usage().inputTokens(), summary.usage().outputTokens()))
                .append('\n')
                .append("예상 비용 $").append(String.format("%.4f",
                    anthropicProperties.estimateCost(inputTokens, outputTokens)));
            if (!result.failedCategoryIds().isEmpty()) {
                content.append("\n난이도 판정 실패 categoryId: ").append(result.failedCategoryIds());
            }
            if (!summary.failedArticleIds().isEmpty()) {
                content.append("\n요약 실패 articleId: ").append(summary.failedArticleIds().stream()
                    .limit(MAX_FAILED_IDS_IN_ALERT).toList());
            }
            batchAlertNotifier.send(content.toString());
        } catch (RuntimeException e) {
            log.warn("[AI 가공] 완료 알림 실패 - {}", e.getMessage());
        }
    }

    private static String tokens(long input, long output) {
        return String.format("입력 %,d / 출력 %,d", input, output);
    }

    /**
     * @param pendingCount      가공을 기다리던 기사 수
     * @param categoryCount     난이도를 판정한 카테고리 수
     * @param failedCategoryIds 난이도를 판정하지 못해 요약하지 않은 카테고리
     */
    public record AiProcessResult(int pendingCount, int categoryCount, List<Long> failedCategoryIds,
                                  long difficultyCalls, TokenUsage difficultyUsage, SummaryResult summary) {

        static AiProcessResult empty() {
            return new AiProcessResult(0, 0, List.of(), 0, new TokenUsage(0, 0),
                new SummaryResult(0, List.of(), 0, new ArticleSummaryService.UsageTotal(0, 0, 0, 0)));
        }
    }
}
