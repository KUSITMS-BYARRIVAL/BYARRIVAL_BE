package com.byarrival.domain.event.service;

import com.byarrival.domain.event.repository.UsageEventLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이용 이벤트 로그(LOG-01)를 사용자 요청과 따로 저장한다. 저장에 실패해도 사용자 흐름은 막지 않고 몇 번 다시 시도한다.
 */
@Slf4j
@Component
public class UsageEventRecorder {

    private static final int MAX_ATTEMPTS = 3;

    private final UsageEventLogRepository repository;
    private final TransactionTemplate transactionTemplate;

    public UsageEventRecorder(UsageEventLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 기사 열람(완독) 이벤트. 날짜는 노선의 서비스일로 남긴다.
     */
    public void recordArticleRead(Long userId, Long routeId, Long articleId, LocalDate serviceDate,
        LocalDateTime occurredAt) {
        retry("ARTICLE_READ routeId=" + routeId + " articleId=" + articleId,
            () -> repository.insertArticleRead(userId, routeId, articleId, serviceDate, occurredAt));
    }

    /**
     * 노선 완주 이벤트. 자정을 넘겨 완주해도 노선의 서비스일로 남긴다(ARR-01).
     */
    public void recordRouteCompleted(Long userId, Long routeId, LocalDate serviceDate, LocalDateTime occurredAt) {
        retry("ROUTE_COMPLETED routeId=" + routeId,
            () -> repository.insertRouteCompleted(userId, routeId, serviceDate, occurredAt));
    }

    /**
     * 앱 접속 이벤트. 날짜는 서비스일이 아니라 KST 자정 기준 날짜이며 날짜당 한 번만 남는다(나의 기록 캘린더 '앱 접속만').
     *
     * @return 저장했거나 이미 있으면 true, 다시 시도해도 실패하면 false
     */
    public boolean recordAppAccess(Long userId, LocalDate accessDate, LocalDateTime occurredAt) {
        return retry("APP_ACCESS userId=" + userId + " date=" + accessDate,
            () -> repository.insertAppAccess(userId, accessDate, occurredAt));
    }

    private boolean retry(String target, Runnable work) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> work.run());
                return true;
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("[UsageEvent] {} 저장 실패 ({}회 시도)", target, MAX_ATTEMPTS, e);
                    return false;
                }
                log.warn("[UsageEvent] {} 저장 실패, 다시 시도 {}/{}: {}", target, attempt, MAX_ATTEMPTS,
                    e.getClass().getSimpleName());
            }
        }
        return false;
    }
}
