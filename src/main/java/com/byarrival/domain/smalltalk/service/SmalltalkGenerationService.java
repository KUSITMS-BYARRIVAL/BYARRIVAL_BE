package com.byarrival.domain.smalltalk.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.smalltalk.config.SmalltalkProperties;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerator.SmalltalkSource;
import com.byarrival.domain.smalltalk.service.SmalltalkValidator.UnsuitableArticleException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 일일 배치에서 카테고리별 인기 기사로 스몰토크를 최대 2건씩 만든다(TALK-01).
 * 인기 순서는 구글 뉴스 Feed 노출 순서이며, 수집 때 이 순서대로 저장하므로 기사 ID 순서와 같다(2026-11-13 결정).
 * 검증에 끝내 실패하거나 대화 소재로 알맞지 않은 기사는 건너뛰고 같은 카테고리의 다음 기사로 대체한다.
 */
@Slf4j
@Service
public class SmalltalkGenerationService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String SMALLTALK_ARTICLE_UNIQUE_CONSTRAINT = "uk_smalltalk_article";

    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final SmalltalkGenerator generator;
    private final SmalltalkValidator validator;
    private final SmalltalkStore store;
    private final ExecutorService executor;
    private final SmalltalkProperties properties;
    private final BatchAlertNotifier batchAlertNotifier;
    private final Clock clock;

    public SmalltalkGenerationService(ArticleQueryService articleQueryService,
        CategoryMappingService categoryMappingService, SmalltalkGenerator generator, SmalltalkValidator validator,
        SmalltalkStore store, @Qualifier("smalltalkGenerationExecutor") ExecutorService executor,
        SmalltalkProperties properties, BatchAlertNotifier batchAlertNotifier, Clock clock) {
        this.articleQueryService = articleQueryService;
        this.categoryMappingService = categoryMappingService;
        this.generator = generator;
        this.validator = validator;
        this.store = store;
        this.executor = executor;
        this.properties = properties;
        this.batchAlertNotifier = batchAlertNotifier;
        this.clock = clock;
    }

    /**
     * @return 카테고리 ID → 그 서비스일에 확보한 스몰토크 수(이번 실행 전에 만든 것 포함)
     */
    public Map<Long, Integer> generate(LocalDate serviceDate) {
        Set<Long> done = store.findArticleIds(serviceDate);
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        Map<Long, List<Article>> byCategory = new LinkedHashMap<>();
        for (Article article : articleQueryService.findRecommendableAll(serviceDate)) {
            byCategory.computeIfAbsent(article.getCategoryId(), id -> new ArrayList<>()).add(article);
        }

        Map<Long, CompletableFuture<Integer>> futures = new LinkedHashMap<>();
        byCategory.forEach((categoryId, articles) -> futures.put(categoryId, CompletableFuture.supplyAsync(
            () -> fillCategory(serviceDate, articles, done, categoryNames.getOrDefault(categoryId, "")), executor)));
        Map<Long, Integer> counts = new LinkedHashMap<>();
        futures.forEach((categoryId, future) -> counts.put(categoryId, future.join()));
        log.info("[AI 스몰토크] serviceDate={} 카테고리별 확보 {}", serviceDate, counts);
        notifyCompleted(serviceDate, counts);
        return counts;
    }

    // 인기 순서대로 내려가며 카테고리당 최대 perCategory건을 채운다
    private int fillCategory(LocalDate serviceDate, List<Article> articles, Set<Long> done, String categoryName) {
        int count = (int) articles.stream().filter(article -> done.contains(article.getId())).count();
        for (Article article : articles) {
            if (count >= properties.perCategory()) {
                break;
            }
            if (done.contains(article.getId())) {
                continue;
            }
            try {
                String hook = generateValid(sourceOf(article, categoryName));
                if (hook != null) {
                    store.save(article.getId(), serviceDate, hook, LocalDateTime.now(clock));
                    count++;
                }
            } catch (DataIntegrityViolationException e) {
                if (!isSmalltalkArticleUniqueViolation(e)) {
                    log.error("[AI 스몰토크 실패] articleId={} 저장 실패", article.getId(), e);
                    continue;
                }
                // 배치 재실행이 겹쳐 그 사이 같은 기사의 스몰토크가 저장된 경우. 이미 있으니 확보한 건수에 넣는다
                log.info("[AI 스몰토크] articleId={} 이미 스몰토크가 있어 건너뜀", article.getId());
                count++;
            } catch (RuntimeException e) {
                log.error("[AI 스몰토크 실패] articleId={} {}", article.getId(), e.getClass().getSimpleName(), e);
            }
        }
        return count;
    }

    /**
     * 검증에 실패하면 어긴 규칙을 알려 주고 같은 기사로 최대 maxRetries회 다시 요청한다.
     *
     * @return 검증을 통과한 hook. 끝내 실패했거나 대화 소재로 알맞지 않은 기사면 null
     */
    private String generateValid(SmalltalkSource source) {
        SmalltalkOutput previous = null;
        String problems = null;
        int attempts = properties.maxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            SmalltalkOutput output;
            try {
                ClaudeResult<SmalltalkOutput> result = previous == null
                    ? generator.generate(source)
                    : generator.regenerate(source, previous, problems);
                output = result.output();
            } catch (BusinessException e) {
                previous = null;
                problems = null;
                continue;
            }
            try {
                return validator.validate(output, source.title());
            } catch (UnsuitableArticleException e) {
                log.info("[AI 스몰토크] articleId={} 대화 소재로 알맞지 않아 다음 기사로", source.articleId());
                return null;
            } catch (BusinessException e) {
                previous = output;
                problems = e.getMessage();
            }
        }
        log.warn("[AI 스몰토크 실패] articleId={} attempts={}", source.articleId(), attempts);
        return null;
    }

    private static boolean isSmalltalkArticleUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIQUE_VIOLATION_SQL_STATE.equals(violation.getSQLState())
                    && SMALLTALK_ARTICLE_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    private static SmalltalkSource sourceOf(Article article, String categoryName) {
        return new SmalltalkSource(article.getId(), article.getCategoryId(), article.getTitle(), categoryName,
            article.getSummaryOneLine(),
            Stream.of(article.getSummarySentence1(), article.getSummarySentence2(), article.getSummarySentence3())
                .filter(Objects::nonNull).toList());
    }

    // 알림이 실패해도 결과에는 영향을 주지 않는다
    private void notifyCompleted(LocalDate serviceDate, Map<Long, Integer> counts) {
        try {
            long shortage = counts.values().stream().filter(count -> count < properties.perCategory()).count();
            batchAlertNotifier.send((shortage == 0 ? "💬 **AI 스몰토크 완료**" : "⚠️ **AI 스몰토크 완료 (부족한 카테고리 있음)**")
                + " 서비스일 " + serviceDate + "\n카테고리별 확보 " + counts);
        } catch (RuntimeException e) {
            log.warn("[AI 스몰토크] 완료 알림 실패 - {}", e.getMessage());
        }
    }
}
