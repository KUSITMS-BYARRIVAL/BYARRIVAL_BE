package com.byarrival.domain.quiz.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.ai.config.AnthropicProperties;
import com.byarrival.domain.ai.exception.AiResponseInvalidException;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.quiz.config.QuizProperties;
import com.byarrival.domain.quiz.dto.QuizCheckOutput;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.exception.QuizErrorCode;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 일일 배치에서 그 서비스일 추천 후보 기사마다 3지선다 퀴즈 1문제를 만들어 저장한다(AI-03, AI-04).
 * 이미 퀴즈가 있는 기사는 건너뛰므로 배치를 다시 돌려도 기사당 1문제다. 실패한 기사는 퀴즈 없이 남고 노선 퀴즈에서 빠진다.
 */
@Slf4j
@Service
public class QuizGenerationService {

    private static final int MAX_FAILED_IDS_IN_ALERT = 30;
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String QUIZ_ARTICLE_UNIQUE_CONSTRAINT = "uk_quiz_article";

    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final QuizGenerator generator;
    private final QuizValidator validator;
    private final QuizChoiceShuffler shuffler;
    private final QuizChecker checker;
    private final QuizStore store;
    private final ExecutorService executor;
    private final QuizProperties properties;
    private final AnthropicProperties anthropicProperties;
    private final BatchAlertNotifier batchAlertNotifier;
    private final Clock clock;

    public QuizGenerationService(ArticleQueryService articleQueryService, CategoryMappingService categoryMappingService,
        QuizGenerator generator, QuizValidator validator, QuizChoiceShuffler shuffler, QuizChecker checker,
        QuizStore store, @Qualifier("quizGenerationExecutor") ExecutorService executor, QuizProperties properties,
        AnthropicProperties anthropicProperties, BatchAlertNotifier batchAlertNotifier, Clock clock) {
        this.articleQueryService = articleQueryService;
        this.categoryMappingService = categoryMappingService;
        this.generator = generator;
        this.validator = validator;
        this.shuffler = shuffler;
        this.checker = checker;
        this.store = store;
        this.executor = executor;
        this.properties = properties;
        this.anthropicProperties = anthropicProperties;
        this.batchAlertNotifier = batchAlertNotifier;
        this.clock = clock;
    }

    public QuizGenerationResult generate(LocalDate serviceDate) {
        List<Article> articles = articleQueryService.findRecommendableAll(serviceDate);
        Set<Long> done = store.findArticleIdsWithQuiz(articles.stream().map(Article::getId).toList());
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        List<QuizSource> sources = articles.stream()
            .filter(article -> !done.contains(article.getId()))
            .map(article -> new QuizSource(article.getId(), article.getTitle(),
                categoryNames.getOrDefault(article.getCategoryId(), ""), article.getSummaryOneLine(),
                Stream.of(article.getSummarySentence1(), article.getSummarySentence2(), article.getSummarySentence3())
                    .filter(Objects::nonNull).toList()))
            .toList();
        if (sources.isEmpty()) {
            log.info("[AI 퀴즈] serviceDate={} 만들 퀴즈 없음", serviceDate);
            return new QuizGenerationResult(0, 0, List.of(), 0, 0, 0);
        }

        Tally tally = new Tally();
        List<CompletableFuture<Outcome>> futures = sources.stream()
            .map(source -> CompletableFuture.supplyAsync(() -> generateOne(source, tally), executor))
            .toList();
        List<Long> failed = new ArrayList<>();
        int succeeded = 0;
        int duplicated = 0;
        for (CompletableFuture<Outcome> future : futures) {
            Outcome outcome = future.join();
            switch (outcome.status()) {
                case SAVED -> succeeded++;
                case DUPLICATED -> duplicated++;
                case FAILED -> failed.add(outcome.articleId());
            }
        }
        QuizGenerationResult result = new QuizGenerationResult(succeeded, duplicated, List.copyOf(failed),
            tally.calls.get(), tally.inputTokens.get(), tally.outputTokens.get());
        log.info("[AI 퀴즈] serviceDate={} 대상 {}건, 성공 {}건, 이미 있음 {}건, 실패 {}건, 호출 {}회", serviceDate,
            sources.size(), succeeded, duplicated, failed.size(), result.calls());
        notifyCompleted(serviceDate, sources.size(), result);
        return result;
    }

    private Outcome generateOne(QuizSource source, Tally tally) {
        try {
            ValidatedQuiz quiz = generateValid(source, tally);
            if (quiz == null) {
                return new Outcome(source.articleId(), OutcomeStatus.FAILED);
            }
            store.save(source.articleId(), quiz, LocalDateTime.now(clock));
            return new Outcome(source.articleId(), OutcomeStatus.SAVED);
        } catch (DataIntegrityViolationException e) {
            if (isQuizArticleUniqueViolation(e)) {
                // 배치 재실행이 겹쳐 그 사이 같은 기사의 퀴즈가 저장된 경우. 기사당 1문제는 지켜졌다
                log.info("[AI 퀴즈] articleId={} 이미 퀴즈가 있어 건너뜀", source.articleId());
                return new Outcome(source.articleId(), OutcomeStatus.DUPLICATED);
            }
            log.error("[AI 퀴즈 실패] articleId={} 저장 실패", source.articleId(), e);
            return new Outcome(source.articleId(), OutcomeStatus.FAILED);
        } catch (RuntimeException e) {
            log.error("[AI 퀴즈 실패] articleId={} {}", source.articleId(), e.getClass().getSimpleName(), e);
            return new Outcome(source.articleId(), OutcomeStatus.FAILED);
        }
    }

    private static boolean isQuizArticleUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIQUE_VIOLATION_SQL_STATE.equals(violation.getSQLState())
                    && QUIZ_ARTICLE_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    /**
     * 검증에 실패하면 어긴 규칙을 알려 주고 최대 maxRetries회 다시 요청한다(AI-05). 호출 자체가 실패하면 처음부터 다시 요청한다.
     */
    private ValidatedQuiz generateValid(QuizSource source, Tally tally) {
        QuizOutput previous = null;
        String problems = null;
        BusinessException lastError = null;
        int attempts = properties.maxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            QuizOutput output;
            try {
                ClaudeResult<QuizOutput> result = previous == null
                    ? generator.generate(source)
                    : generator.regenerate(source, previous, problems);
                tally.add(result.usage().inputTokens(), result.usage().outputTokens());
                output = result.output();
            } catch (BusinessException e) {
                tally.addFailed(e);
                lastError = e;
                previous = null;
                problems = null;
                continue;
            }
            ValidatedQuiz quiz;
            try {
                quiz = shuffler.shuffle(validator.validate(output, source));
            } catch (BusinessException e) {
                lastError = e;
                previous = output;
                problems = e.getMessage();
                continue;
            }
            QuizCheckOutput check;
            try {
                ClaudeResult<QuizCheckOutput> result = checker.check(source, quiz);
                tally.add(result.usage().inputTokens(), result.usage().outputTokens());
                check = result.output();
            } catch (BusinessException e) {
                // 검수를 못 하면 맞는 퀴즈인지 알 수 없으므로 저장하지 않고 처음부터 다시 만든다
                tally.addFailed(e);
                lastError = e;
                previous = null;
                problems = null;
                continue;
            }
            String checkProblems = checkProblems(check, quiz);
            if (checkProblems == null) {
                return quiz;
            }
            lastError = new BusinessException(QuizErrorCode.QUIZ_RESPONSE_INVALID, checkProblems);
            previous = output;
            problems = checkProblems;
        }
        log.warn("[AI 퀴즈 실패] articleId={} attempts={} reason={}", source.articleId(), attempts,
            lastError.getMessage().replace('\n', ' '));
        return null;
    }

    /**
     * 검수 결과 정답 선택지가 저장할 정답 하나뿐이고 해설이 그 정답을 설명해야 통과다. 어긋나면 다시 만들 때 알려 줄 문제를 돌려준다.
     * 번호는 섞은 순서라 출제 AI는 모르므로 선택지 글로 알려 준다.
     */
    static String checkProblems(QuizCheckOutput check, ValidatedQuiz quiz) {
        List<String> problems = new ArrayList<>();
        List<Integer> correct = check == null || check.correctChoices() == null
            ? List.of() : check.correctChoices().stream().filter(Objects::nonNull).distinct().toList();
        String answer = quiz.choices().get(quiz.answerSeq() - 1);
        if (!correct.equals(List.of(quiz.answerSeq()))) {
            List<String> correctTexts = correct.stream()
                .filter(seq -> seq >= 1 && seq <= quiz.choices().size())
                .map(seq -> "'" + quiz.choices().get(seq - 1) + "'")
                .toList();
            problems.add("다른 사람이 요약만 보고 풀었을 때 올바른 답으로 본 선택지가 "
                + (correctTexts.isEmpty() ? "없었습니다" : String.join(", ", correctTexts) + "였습니다")
                + ". 정답 '" + answer + "' 하나만 문제의 답이 되고 오답은 확실히 틀리게, 문제가 묻는 것과 정답이 맞게 다시 쓰세요.");
        }
        if (check == null || !check.explanationMatches()) {
            problems.add("해설이 정답 '" + answer + "'의 근거를 설명하지 않습니다. 정답의 근거를 설명하세요.");
        }
        if (problems.isEmpty()) {
            return null;
        }
        if (check != null && check.reason() != null && !check.reason().isBlank()) {
            problems.add("검수 의견: " + check.reason().strip());
        }
        return String.join("\n", problems);
    }

    // 알림이 실패해도 퀴즈 결과에는 영향을 주지 않는다
    private void notifyCompleted(LocalDate serviceDate, int targets, QuizGenerationResult result) {
        try {
            StringBuilder content = new StringBuilder()
                .append(result.failedArticleIds().isEmpty() ? "🧩 **AI 퀴즈 완료**" : "⚠️ **AI 퀴즈 완료 (실패 있음)**")
                .append(" 서비스일 ").append(serviceDate).append('\n')
                .append("대상 ").append(targets).append("건 · 성공 ").append(result.succeeded())
                .append(" · 이미 있음 ").append(result.duplicated())
                .append(" · 실패 ").append(result.failedArticleIds().size())
                .append(" · 호출 ").append(result.calls()).append("회")
                .append(String.format(" · 토큰 입력 %,d / 출력 %,d", result.inputTokens(), result.outputTokens()))
                .append('\n')
                .append("예상 비용 $").append(String.format("%.4f",
                    anthropicProperties.estimateCost(result.inputTokens(), result.outputTokens())));
            if (!result.failedArticleIds().isEmpty()) {
                content.append("\n퀴즈 실패 articleId: ")
                    .append(result.failedArticleIds().stream().limit(MAX_FAILED_IDS_IN_ALERT).toList());
            }
            batchAlertNotifier.send(content.toString());
        } catch (RuntimeException e) {
            log.warn("[AI 퀴즈] 완료 알림 실패 - {}", e.getMessage());
        }
    }

    /**
     * @param duplicated 그 사이 다른 실행이 같은 기사의 퀴즈를 저장해 건너뛴 수
     */
    public record QuizGenerationResult(int succeeded, int duplicated, List<Long> failedArticleIds, long calls,
                                       long inputTokens, long outputTokens) {
    }

    private record Outcome(Long articleId, OutcomeStatus status) {
    }

    private enum OutcomeStatus {
        SAVED, DUPLICATED, FAILED
    }

    private static final class Tally {

        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();

        void add(long input, long output) {
            calls.incrementAndGet();
            inputTokens.addAndGet(input);
            outputTokens.addAndGet(output);
        }

        // 응답을 받았지만 쓸 수 없던 호출은 쓴 토큰을 넣고, 응답을 받지 못한 호출은 호출 수만 센다
        void addFailed(BusinessException e) {
            if (e instanceof AiResponseInvalidException invalid) {
                add(invalid.getUsage().inputTokens(), invalid.getUsage().outputTokens());
            } else {
                add(0, 0);
            }
        }
    }
}
