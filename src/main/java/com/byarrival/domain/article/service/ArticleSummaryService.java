package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleSummaryProperties;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.service.ArticleSummaryStore.SummaryInput;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 기사마다 배정된 난이도로 3문장 요약을 생성·검증·저장한다(AI-01, AI-02). 호출은 기사 단위로 병렬 처리한다.
 * 실패한 기사는 요약 없이 남아 추천 대상이 되지 않는다. 실패 기록과 재처리는 AI-05에서 한다.
 */
@Slf4j
@Service
public class ArticleSummaryService {

    private final ArticleSummaryGenerator generator;
    private final ArticleSummaryValidator validator;
    private final ArticleSummaryStore store;
    private final ExecutorService summaryExecutor;
    private final ArticleSummaryProperties properties;

    public ArticleSummaryService(ArticleSummaryGenerator generator, ArticleSummaryValidator validator,
        ArticleSummaryStore store, @Qualifier("articleSummaryExecutor") ExecutorService summaryExecutor,
        ArticleSummaryProperties properties) {
        this.properties = properties;
        this.generator = generator;
        this.validator = validator;
        this.store = store;
        this.summaryExecutor = summaryExecutor;
    }

    public SummaryResult summarize(List<SummaryTarget> targets) {
        if (targets.isEmpty()) {
            return new SummaryResult(0, List.of(), 0, new UsageTotal(0, 0, 0, 0));
        }
        UsageTally tally = new UsageTally();
        Map<Long, ArticleDifficulty> difficulties = targets.stream()
            .collect(Collectors.toMap(SummaryTarget::articleId, SummaryTarget::difficulty, (first, second) -> first));
        List<SummaryInput> inputs = store.loadInputs(difficulties.keySet());

        List<CompletableFuture<Outcome>> futures = inputs.stream()
            .map(input -> CompletableFuture.supplyAsync(
                () -> summarizeOne(input, difficulties.get(input.articleId()), tally), summaryExecutor))
            .toList();
        List<Outcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

        int succeeded = 0;
        List<Long> failed = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            switch (outcome.status()) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed.add(outcome.articleId());
                case SKIPPED -> { }
            }
        }
        int skipped = difficulties.size() - succeeded - failed.size();
        UsageTotal usage = tally.total();
        SummaryResult result = new SummaryResult(succeeded, List.copyOf(failed), skipped, usage);
        log.info("[AI 요약] 대상 {}건, 성공 {}건, 실패 {}건, 건너뜀 {}건, 호출 {}회, 입력 토큰 {}, 출력 토큰 {}",
            difficulties.size(), succeeded, failed.size(), skipped, usage.calls(), usage.inputTokens(),
            usage.outputTokens());
        return result;
    }

    private Outcome summarizeOne(SummaryInput input, ArticleDifficulty difficulty, UsageTally tally) {
        try {
            ValidatedSummary summary = generateValid(input, difficulty, tally);
            if (summary == null) {
                return new Outcome(input.articleId(), OutcomeStatus.FAILED);
            }
            boolean saved = store.save(input.articleId(), difficulty, summary);
            return new Outcome(input.articleId(), saved ? OutcomeStatus.SUCCEEDED : OutcomeStatus.SKIPPED);
        } catch (RuntimeException e) {
            log.error("[AI 요약 실패] articleId={} {}", input.articleId(), e.getClass().getSimpleName(), e);
            return new Outcome(input.articleId(), OutcomeStatus.FAILED);
        }
    }

    /**
     * 최초 호출 후 검증에 실패하면 어긴 규칙을 알려 주고 최대 maxRetries회 다시 요청한다(AI-05).
     * 호출 자체가 실패하면 피드백 없이 처음부터 다시 요청한다. 429·5xx는 SDK가 먼저 재시도한다.
     *
     * @return 검증을 통과한 요약. 끝내 실패하면 null
     */
    private ValidatedSummary generateValid(SummaryInput input, ArticleDifficulty difficulty, UsageTally tally) {
        ArticleSummaryOutput previous = null;
        String problems = null;
        BusinessException lastError = null;
        int attempts = properties.maxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            ArticleSummaryOutput output;
            try {
                ClaudeResult<ArticleSummaryOutput> result = previous == null
                    ? generator.generate(input.title(), input.content(), difficulty)
                    : generator.regenerate(input.title(), input.content(), difficulty, previous, problems);
                tally.add(result.usage(), attempt > 1);
                output = result.output();
            } catch (BusinessException e) {
                // 응답을 받지 못했거나 잘린 호출. 토큰 수를 알 수 없어 호출 수만 센다
                tally.addFailedCall(attempt > 1);
                lastError = e;
                previous = null;
                problems = null;
                continue;
            }
            try {
                ValidatedSummary summary = validator.validate(output, input.title(), difficulty);
                if (attempt > 1) {
                    log.info("[AI 요약] articleId={} {}번째 시도에 검증 통과", input.articleId(), attempt);
                }
                return summary;
            } catch (BusinessException e) {
                lastError = e;
                previous = output;
                problems = e.getMessage();
            }
        }
        log.warn("[AI 요약 실패] articleId={} attempts={} code={} reason={}", input.articleId(), attempts,
            lastError.getErrorCode(), lastError.getMessage().replace('\n', ' '));
        return null;
    }

    public record SummaryTarget(Long articleId, ArticleDifficulty difficulty) {
    }

    /**
     * @param skipped 정제가 끝나지 않았거나 이미 요약한 기사 수
     */
    public record SummaryResult(int succeeded, List<Long> failedArticleIds, int skipped, UsageTotal usage) {
    }

    /**
     * @param retries 검증 실패나 호출 실패로 다시 요청한 횟수
     */
    public record UsageTotal(long calls, long retries, long inputTokens, long outputTokens) {
    }

    // 여러 스레드가 동시에 더하므로 원자 연산으로 센다
    private static final class UsageTally {

        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong retries = new AtomicLong();
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();

        void add(TokenUsage usage, boolean retry) {
            addFailedCall(retry);
            inputTokens.addAndGet(usage.inputTokens());
            outputTokens.addAndGet(usage.outputTokens());
        }

        void addFailedCall(boolean retry) {
            calls.incrementAndGet();
            if (retry) {
                retries.incrementAndGet();
            }
        }

        UsageTotal total() {
            return new UsageTotal(calls.get(), retries.get(), inputTokens.get(), outputTokens.get());
        }
    }

    private record Outcome(Long articleId, OutcomeStatus status) {
    }

    private enum OutcomeStatus {
        SUCCEEDED, FAILED, SKIPPED
    }
}
