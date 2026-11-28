package com.byarrival.domain.batch.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.article.service.ArticleCollectService;
import com.byarrival.domain.article.service.ArticleSaveService;
import com.byarrival.domain.batch.config.BatchConfig;
import com.byarrival.domain.batch.dto.response.BatchRefreshResponse;
import com.byarrival.domain.batch.entity.BatchRun;
import com.byarrival.domain.batch.entity.BatchRunStatus;
import com.byarrival.domain.batch.exception.BatchErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자 새로고침. 다음 서비스일 기사를 지우고 일일 배치를 처음부터 다시 실행한다.
 * 다음 서비스일 기사는 아직 노출 전이라 노선·읽은 기록이 없어 언제 지워도 된다.
 */
@Slf4j
@Service
public class BatchRefreshService {

    private final BatchRunService batchRunService;
    private final ArticleSaveService articleSaveService;
    private final ArticleCollectService articleCollectService;
    private final DailyBatchService dailyBatchService;
    private final BatchMonitor batchMonitor;
    private final TransactionTemplate transactionTemplate;
    private final Executor refreshExecutor;
    private final Clock clock;

    public BatchRefreshService(BatchRunService batchRunService, ArticleSaveService articleSaveService,
        ArticleCollectService articleCollectService, DailyBatchService dailyBatchService, BatchMonitor batchMonitor,
        TransactionTemplate transactionTemplate, @Qualifier(BatchConfig.REFRESH_EXECUTOR) Executor refreshExecutor,
        Clock clock) {
        this.batchRunService = batchRunService;
        this.articleSaveService = articleSaveService;
        this.articleCollectService = articleCollectService;
        this.dailyBatchService = dailyBatchService;
        this.batchMonitor = batchMonitor;
        this.transactionTemplate = transactionTemplate;
        this.refreshExecutor = refreshExecutor;
        this.clock = clock;
    }

    /**
     * 배치 이력 교체와 기사 삭제를 한 트랜잭션으로 끝낸 뒤, 배치는 백그라운드에서 실행하고 바로 돌아온다.
     */
    public BatchRefreshResponse refresh(Long adminId) {
        LocalDate serviceDate = nextServiceDate();
        Prepared prepared;
        try {
            prepared = transactionTemplate.execute(status -> {
                Long batchRunId = batchRunService.startReplacing(serviceDate);
                return new Prepared(batchRunId, articleSaveService.deleteByServiceDate(serviceDate));
            });
        } catch (DataIntegrityViolationException e) {
            // 정기 배치나 다른 새로고침이 같은 순간에 시작해 실행 중 배치 제약에 걸린 경우
            log.info("[BatchRefresh] serviceDate={} 다른 실행이 먼저 시작해 거절", serviceDate);
            throw new BusinessException(BatchErrorCode.BATCH_RUNNING);
        }
        log.info("[BatchRefresh] serviceDate={} adminId={} 기사 {}건 삭제 후 배치 다시 실행",
            serviceDate, adminId, prepared.deletedArticles());
        batchMonitor.refreshStarted(serviceDate, adminId, prepared.deletedArticles());
        try {
            refreshExecutor.execute(() -> process(prepared.batchRunId(), serviceDate));
        } catch (RejectedExecutionException e) {
            // 실행 중으로 남으면 다음 정기 배치와 새로고침이 모두 막히므로 실패로 남긴다
            batchRunService.fail(prepared.batchRunId(), "새로고침 실행 대기열이 가득 차 시작하지 못함");
            throw new BusinessException(BatchErrorCode.BATCH_RUNNING);
        }
        // 다시 날짜를 계산하면 04:00을 넘긴 순간 다른 서비스일을 볼 수 있으므로 방금 시작한 실행으로 응답한다
        return BatchRefreshResponse.of(batchRunService.get(prepared.batchRunId()), null);
    }

    /**
     * 다음 서비스일 배치 상태. 04:00을 넘겨 이전 서비스일 배치가 아직 돌고 있으면 그 실행을 보여 줘 버튼을 막는다.
     */
    @Transactional(readOnly = true)
    public BatchRefreshResponse getStatus() {
        Optional<BatchRun> running = batchRunService.findRunning();
        if (running.isPresent()) {
            return BatchRefreshResponse.of(running.get(), null);
        }
        LocalDate serviceDate = nextServiceDate();
        Optional<BatchRun> latest = batchRunService.findLatest(serviceDate);
        if (latest.isEmpty()) {
            return BatchRefreshResponse.none(serviceDate);
        }
        BatchRun batchRun = latest.get();
        Long recommendableCount = batchRun.getStatus() == BatchRunStatus.COMPLETED
            ? articleCollectService.checkRecommendableFill(serviceDate).totalCount()
            : null;
        return BatchRefreshResponse.of(batchRun, recommendableCount);
    }

    private void process(Long batchRunId, LocalDate serviceDate) {
        try {
            dailyBatchService.process(batchRunId, serviceDate);
        } catch (RuntimeException e) {
            // 배치 결과 기록 자체가 실패한 경우. 정기 실행과 같이 직접 알린다
            log.error("[BatchRefresh] serviceDate={} 비정상 종료", serviceDate, e);
            batchMonitor.crashed(e);
        }
    }

    // 정기 배치(23:45)가 만드는 서비스일과 같다
    private LocalDate nextServiceDate() {
        return ServiceDay.today(clock).plusDays(1);
    }

    private record Prepared(Long batchRunId, int deletedArticles) {
    }
}
