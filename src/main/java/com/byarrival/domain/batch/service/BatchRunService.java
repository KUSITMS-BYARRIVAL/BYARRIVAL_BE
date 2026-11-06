package com.byarrival.domain.batch.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.batch.entity.BatchRun;
import com.byarrival.domain.batch.entity.BatchRunStatus;
import com.byarrival.domain.batch.exception.BatchErrorCode;
import com.byarrival.domain.batch.repository.BatchRunRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일일 배치의 실행 이력을 기록한다. 같은 서비스일 배치가 두 번 돌지 않게 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchRunService {

    private final BatchRunRepository batchRunRepository;
    private final Clock clock;

    // 실행 중이거나 완료된 배치가 있으면 시작하지 않는다. 실패한 배치는 다시 시작할 수 있다.
    // 동시에 시작해도 uk_batch_run_service_date_active 가 하나만 저장되게 막으므로,
    // 저장이 제약에 걸리면 다른 쪽이 먼저 시작한 것으로 본다.
    // 제약 위반을 이 메서드에서 처리하려고 트랜잭션을 걸지 않는다 (저장은 리포지토리 트랜잭션에서 커밋된다).
    public Optional<Long> start(LocalDate serviceDate) {
        if (batchRunRepository.existsByServiceDateAndStatusIn(
            serviceDate, EnumSet.of(BatchRunStatus.RUNNING, BatchRunStatus.COMPLETED))) {
            return Optional.empty();
        }
        try {
            BatchRun batchRun = batchRunRepository.saveAndFlush(BatchRun.start(serviceDate, LocalDateTime.now(clock)));
            return Optional.of(batchRun.getId());
        } catch (DataIntegrityViolationException e) {
            log.info("[BatchRun] serviceDate={} 배치를 다른 실행이 먼저 시작해 건너뜀", serviceDate);
            return Optional.empty();
        }
    }

    @Transactional
    public void complete(Long batchRunId) {
        getBatchRun(batchRunId).complete(LocalDateTime.now(clock));
    }

    @Transactional
    public void fail(Long batchRunId, String errorMessage) {
        getBatchRun(batchRunId).fail(LocalDateTime.now(clock), errorMessage);
    }

    private BatchRun getBatchRun(Long batchRunId) {
        return batchRunRepository.findById(batchRunId)
            .orElseThrow(() -> new BusinessException(BatchErrorCode.BATCH_RUN_NOT_FOUND));
    }
}
