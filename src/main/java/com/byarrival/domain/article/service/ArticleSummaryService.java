package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiRateLimitedException;
import com.byarrival.domain.ai.exception.AiResponseInvalidException;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleSummaryProperties;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.exception.ArticleSummaryInvalidException;
import com.byarrival.domain.article.service.ArticleSummaryStore.SummaryInput;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

    // SDK 재시도 후에도 429인 호출을 시도 횟수에 넣지 않고 다시 요청하는 최대 횟수. 한도 초과가 계속돼 배치가 끝나지 않는 것을 막는다
    static final int MAX_RATE_LIMITED_RETRIES = 3;
    // 수선이 연달아 이만큼 실패하면 더 수선하지 않고 본문과 함께 다시 쓰게 한다.
    // 11-22·11-23 배치에서 수선이 같은 답을 되풀이하거나 1~2자를 끝내 못 줄여 실패한 기사가 4건이었다
    static final int MAX_REPAIRS_IN_ROW = 2;

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
            return new SummaryResult(0, List.of(), 0, new UsageTotal(0, 0, TokenUsage.ZERO));
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
        log.info("[AI 요약] 대상 {}건, 성공 {}건, 실패 {}건, 건너뜀 {}건, 호출 {}회, 토큰 {}",
            difficulties.size(), succeeded, failed.size(), skipped, usage.calls(), usage.tokens().describe());
        return result;
    }

    private Outcome summarizeOne(SummaryInput input, ArticleDifficulty difficulty, UsageTally tally) {
        try {
            ValidatedSummary summary = generateValid(input, difficulty, tally);
            if (summary == null) {
                return new Outcome(input.articleId(), OutcomeStatus.FAILED);
            }
            // s2·s3가 제목·s1·서로와 겹치는지 기준값을 정하기 전까지 거부하지 않고 측정만 한다
            log.info("[AI 요약 겹침] articleId={} {}", input.articleId(),
                SummaryOverlap.describe(SummaryOverlap.scores(input.title(), summary.sentences())));
            boolean saved = store.save(input.articleId(), difficulty, summary);
            return new Outcome(input.articleId(), saved ? OutcomeStatus.SUCCEEDED : OutcomeStatus.SKIPPED);
        } catch (RuntimeException e) {
            log.error("[AI 요약 실패] articleId={} {}", input.articleId(), e.getClass().getSimpleName(), e);
            return new Outcome(input.articleId(), OutcomeStatus.FAILED);
        }
    }

    /**
     * 최초 호출 후 검증에 실패하면 어긴 규칙을 알려 주고 최대 maxRetries회 다시 요청한다(AI-05).
     * 형식만 어겼으면 본문 없이 이전 답만 고치게 하고(수선), 근거·숫자처럼 본문이 필요한 규칙을 어겼으면 본문과 함께 다시 쓰게 한다.
     * 수선이 이전과 같은 답을 내거나 연달아 실패하면 같은 수선을 되풀이하지 않고 본문과 함께 다시 쓰게 한다.
     * 호출 자체가 실패하면 피드백 없이 처음부터 다시 요청한다. 429·5xx는 SDK가 먼저 재시도한다.
     * SDK 재시도 후에도 429면 토큰을 쓰지 않았으므로 시도 횟수에 넣지 않고, 따로 정한 횟수까지 다시 요청한다.
     *
     * @return 검증을 통과한 요약. 끝내 실패하면 null
     */
    private ValidatedSummary generateValid(SummaryInput input, ArticleDifficulty difficulty, UsageTally tally) {
        ArticleSummaryOutput previous = null;
        String problems = null;
        boolean repair = false;
        BusinessException lastError = null;
        int attempts = properties.maxRetries() + 1;
        int rateLimited = 0;
        int failedRepairsInRow = 0;
        // 시도 번호(attempt)는 429를 세지 않으므로, 재요청 집계는 실제 호출 순번으로 한다
        int calls = 0;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String mode = previous == null ? "full" : repair ? "repair" : "regenerate";
            boolean retry = calls++ > 0;
            ArticleSummaryOutput output;
            TokenUsage usage;
            try {
                ClaudeResult<ArticleSummaryOutput> result = switch (mode) {
                    case "full" -> generator.generate(input.title(), input.content(), difficulty);
                    case "repair" -> generator.repair(input.title(), difficulty, previous, problems);
                    default -> generator.regenerate(input.title(), input.content(), difficulty, previous, problems);
                };
                usage = result.usage();
                tally.add(usage, retry);
                output = result.output();
            } catch (AiRateLimitedException e) {
                tally.add(TokenUsage.ZERO, retry);
                lastError = e;
                if (rateLimited < MAX_RATE_LIMITED_RETRIES) {
                    rateLimited++;
                    log.info("[AI 요약 요청 한도 초과] articleId={} attempt={}/{} mode={} 시도 횟수에 넣지 않고 다시 요청 {}/{}",
                        input.articleId(), attempt, attempts, mode, rateLimited, MAX_RATE_LIMITED_RETRIES);
                    attempt--;
                    continue;
                }
                log.info("[AI 요약 시도 실패] articleId={} attempt={}/{} mode={} 호출 실패 code={} reason={}",
                    input.articleId(), attempt, attempts, mode, e.getErrorCode(), oneLine(e.getMessage()));
                continue;
            } catch (BusinessException e) {
                // 잘렸거나 쓸 수 없는 응답은 비용이 나갔으므로 쓴 토큰을 넣고, 응답을 받지 못한 호출은 호출 수만 센다
                TokenUsage failedUsage = AiResponseInvalidException.usageOf(e);
                tally.add(failedUsage, retry);
                log.info("[AI 요약 시도 실패] articleId={} attempt={}/{} mode={} 호출 실패 code={} outputTokens={} reason={}",
                    input.articleId(), attempt, attempts, mode, e.getErrorCode(), failedUsage.outputTokens(),
                    oneLine(e.getMessage()));
                lastError = e;
                // 수선 호출이 잘렸으면 이전 답은 그대로 두고 다시 수선한다. 처음부터 다시 쓰는 것보다 싸다
                if (!"repair".equals(mode)) {
                    previous = null;
                    problems = null;
                }
                continue;
            }
            try {
                ValidatedSummary summary = validator.validate(output, input.title(), input.content(), difficulty);
                if (attempt > 1) {
                    log.info("[AI 요약] articleId={} {}번째 시도에 검증 통과 mode={}", input.articleId(), attempt, mode);
                }
                return summary;
            } catch (ArticleSummaryInvalidException e) {
                // 재요청 비용을 줄이려면 어떤 규칙이 얼마나 자주 어겨지는지 알아야 한다. 사유는 " | "로 이어 한 줄로 남긴다
                log.info("[AI 요약 시도 실패] articleId={} attempt={}/{} mode={} 검증 실패 inputTokens={} cacheReadTokens={} outputTokens={} problems={}",
                    input.articleId(), attempt, attempts, mode, usage.inputTokens(), usage.cacheReadTokens(),
                    usage.outputTokens(),
                    oneLine(e.getMessage()));
                lastError = e;
                boolean repaired = "repair".equals(mode);
                failedRepairsInRow = repaired ? failedRepairsInRow + 1 : 0;
                boolean stuck = repaired && (output.equals(previous) || failedRepairsInRow >= MAX_REPAIRS_IN_ROW);
                if (stuck && !e.isNeedsArticle()) {
                    log.info("[AI 요약] articleId={} 수선이 같은 답을 내거나 {}번 연달아 실패해 본문과 함께 다시 씁니다",
                        input.articleId(), failedRepairsInRow);
                }
                previous = output;
                problems = e.getMessage();
                repair = !e.isNeedsArticle() && !stuck;
                if (!repair) {
                    failedRepairsInRow = 0;
                }
            }
        }
        log.warn("[AI 요약 실패] articleId={} attempts={} code={} reason={}", input.articleId(), attempts,
            lastError.getErrorCode(), lastError.getMessage().replace('\n', ' '));
        return null;
    }

    // \r이 남으면 터미널에서 줄 앞부분을 덮어쓰므로 \r\n, \r, \n을 모두 구분자로 바꾼다
    private static String oneLine(String message) {
        return message == null ? "" : message.replaceAll("\\R", " | ");
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
     * @param tokens  검증에 실패하거나 잘린 응답까지 포함해 쓴 토큰
     */
    public record UsageTotal(long calls, long retries, TokenUsage tokens) {
    }

    // 여러 스레드가 동시에 더하므로 원자 연산으로 센다
    private static final class UsageTally {

        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong retries = new AtomicLong();
        private final AtomicReference<TokenUsage> tokens = new AtomicReference<>(TokenUsage.ZERO);

        void add(TokenUsage usage, boolean retry) {
            calls.incrementAndGet();
            if (retry) {
                retries.incrementAndGet();
            }
            tokens.accumulateAndGet(usage, TokenUsage::plus);
        }

        UsageTotal total() {
            return new UsageTotal(calls.get(), retries.get(), tokens.get());
        }
    }

    private record Outcome(Long articleId, OutcomeStatus status) {
    }

    private enum OutcomeStatus {
        SUCCEEDED, FAILED, SKIPPED
    }
}
