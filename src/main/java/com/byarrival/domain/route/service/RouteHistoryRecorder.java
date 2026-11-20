package com.byarrival.domain.route.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.route.entity.ConditionHistory;
import com.byarrival.domain.route.entity.RecommendationLog;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.repository.ConditionHistoryRepository;
import com.byarrival.domain.route.repository.RecommendationLogRepository;
import com.byarrival.domain.route.repository.RouteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 컨디션 이력과 추천 로그를 노선 트랜잭션과 따로 저장한다. 저장에 실패해도 노선 생성·시작하기는 막지 않고 몇 번 다시 시도한다.
 */
@Slf4j
@Component
public class RouteHistoryRecorder {

    private static final int MAX_ATTEMPTS = 3;

    private final ConditionHistoryRepository conditionHistoryRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final RouteRepository routeRepository;
    private final TransactionTemplate transactionTemplate;

    public RouteHistoryRecorder(ConditionHistoryRepository conditionHistoryRepository,
        RecommendationLogRepository recommendationLogRepository, RouteRepository routeRepository,
        PlatformTransactionManager transactionManager) {
        this.conditionHistoryRepository = conditionHistoryRepository;
        this.recommendationLogRepository = recommendationLogRepository;
        this.routeRepository = routeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 노선을 만든 뒤 컨디션 이력과 추천 로그를 남긴다.
     */
    public void recordCreated(Route route, RouteRecommendation recommendation, List<Long> interestCategoryIds) {
        retry("condition_history routeId=" + route.getId(), () -> {
            if (!conditionHistoryRepository.existsByRouteId(route.getId())) {
                conditionHistoryRepository.save(ConditionHistory.of(route));
            }
        });
        retry("recommendation_log routeId=" + route.getId(), () -> {
            if (recommendationLogRepository.findByRouteId(route.getId()).isEmpty()) {
                recommendationLogRepository.save(buildLog(route.getId(), route.getUserId(), route.getServiceDate(),
                    route.getCondition(), route.getTargetMinutes(), interestCategoryIds, recommendation));
            }
        });
    }

    /**
     * 탑승 전에 컨디션을 다시 골라 노선을 다시 만든 뒤 컨디션 이력과 추천 로그를 새 값으로 바꾼다. 없으면 새로 남긴다.
     * 노선을 잠그고 다시 읽어, 그 사이 다른 요청이 다른 컨디션으로 다시 만들었으면 늦게 온 이 기록은 남기지 않는다.
     * 추천 로그는 그 사이 시작하기로 남긴 담은 카드 기록을 지우지 않도록 카드 선택 단계일 때만 바꾼다.
     */
    public void recordRebuilt(Route route, RouteRecommendation recommendation, List<Long> interestCategoryIds) {
        retry("condition_history rebuild routeId=" + route.getId(), () -> {
            if (!isLatestRebuild(route, false)) {
                return;
            }
            conditionHistoryRepository.findByRouteId(route.getId())
                .ifPresentOrElse(history -> history.changeCondition(route.getCondition()),
                    () -> conditionHistoryRepository.save(ConditionHistory.of(route)));
        });
        retry("recommendation_log rebuild routeId=" + route.getId(), () -> {
            if (!isLatestRebuild(route, true)) {
                return;
            }
            RecommendationLog rebuilt = buildLog(route.getId(), route.getUserId(), route.getServiceDate(),
                route.getCondition(), route.getTargetMinutes(), interestCategoryIds, recommendation);
            recommendationLogRepository.findByRouteId(route.getId())
                .ifPresentOrElse(entry -> entry.replaceRecommendation(rebuilt),
                    () -> recommendationLogRepository.save(rebuilt));
        });
    }

    private boolean isLatestRebuild(Route rebuilt, boolean requireCardSelection) {
        return routeRepository.findForUpdate(rebuilt.getId(), rebuilt.getUserId())
            .filter(current -> current.getCondition() == rebuilt.getCondition())
            .filter(current -> !requireCardSelection || current.getStatus() == RouteStatus.CREATED)
            .isPresent();
    }

    /**
     * 추천 결과가 0건이라 노선을 만들지 않은 경우도 추천 로그를 남긴다.
     */
    public void recordEmpty(Long userId, LocalDate serviceDate, RouteCondition condition, int targetMinutes,
        List<Long> interestCategoryIds, RouteRecommendation recommendation) {
        retry("recommendation_log userId=" + userId + " (0건)", () -> recommendationLogRepository.save(
            buildLog(null, userId, serviceDate, condition, targetMinutes, interestCategoryIds, recommendation)));
    }

    /**
     * 시작하기 시점에 담은 카드(담은 순서)와 합계 시간을 추천 로그에 남긴다.
     */
    public void recordBoarding(Long routeId, List<Long> cartArticleIds, BigDecimal cartMinutes,
        LocalDateTime boardedAt) {
        retry("recommendation_log boarding routeId=" + routeId, () -> recommendationLogRepository.findByRouteId(routeId)
            .ifPresent(entry -> entry.recordBoarding(cartArticleIds, cartMinutes, boardedAt)));
    }

    private RecommendationLog buildLog(Long routeId, Long userId, LocalDate serviceDate, RouteCondition condition,
        int targetMinutes, List<Long> interestCategoryIds, RouteRecommendation recommendation) {
        return RecommendationLog.builder()
            .routeId(routeId)
            .userId(userId)
            .serviceDate(serviceDate)
            .condition(condition)
            .targetMinutes(targetMinutes)
            .interestCategoryIds(interestCategoryIds)
            .candidateCount(recommendation.candidateCount())
            .candidateHardCount(recommendation.candidateHardCount())
            .candidateEasyCount(recommendation.candidateEasyCount())
            .fallbackStep(recommendation.isEmpty() ? null : recommendation.fallbackStep())
            .selectedArticleIds(recommendation.selected().stream().map(RouteCandidate::articleId).toList())
            .selectedHardCount(recommendation.selectedCount(ArticleDifficulty.HARD))
            .selectedEasyCount(recommendation.selectedCount(ArticleDifficulty.EASY))
            .selectedMinutes(recommendation.selectedMinutes())
            .poolSize(recommendation.pool().size())
            .build();
    }

    private void retry(String target, Runnable work) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> work.run());
                return;
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("[RouteHistory] {} 저장 실패 ({}회 시도)", target, MAX_ATTEMPTS, e);
                    return;
                }
                log.warn("[RouteHistory] {} 저장 실패, 다시 시도 {}/{}: {}", target, attempt, MAX_ATTEMPTS,
                    e.getClass().getSimpleName());
            }
        }
    }
}
