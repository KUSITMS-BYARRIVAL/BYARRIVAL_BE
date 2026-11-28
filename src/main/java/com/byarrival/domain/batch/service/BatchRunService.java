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

    /**
     * 운영자 새로고침용 시작. 완료된 배치는 대체됨으로 바꾸고 새 실행을 시작한다. 실행 중이면 거절한다.
     * 호출하는 쪽 트랜잭션에 참여해 기사 삭제와 함께 커밋되게 한다. 동시에 시작해 제약에 걸리면
     * {@link org.springframework.dao.DataIntegrityViolationException}이 그대로 올라간다.
     */
    @Transactional
    public Long startReplacing(LocalDate serviceDate) {
        // 04:00 직전에 시작한 배치는 서비스일이 바뀐 뒤에도 돌고 있으므로, 대상 날짜와 상관없이 실행 중이면 막는다
        if (batchRunRepository.existsByStatus(BatchRunStatus.RUNNING)) {
            throw new BusinessException(BatchErrorCode.BATCH_RUNNING);
        }
        batchRunRepository.findByServiceDateAndStatus(serviceDate, BatchRunStatus.COMPLETED)
            .forEach(BatchRun::replace);
        // 대체 표시가 새 실행 저장보다 먼저 반영돼야 유니크 제약에 걸리지 않는다
        batchRunRepository.flush();
        return batchRunRepository.saveAndFlush(BatchRun.start(serviceDate, LocalDateTime.now(clock))).getId();
    }

    @Transactional(readOnly = true)
    public Optional<BatchRun> findLatest(LocalDate serviceDate) {
        return batchRunRepository.findFirstByServiceDateOrderByIdDesc(serviceDate);
    }

    @Transactional(readOnly = true)
    public Optional<BatchRun> findRunning() {
        return batchRunRepository.findFirstByStatusOrderByIdDesc(BatchRunStatus.RUNNING);
    }

    @Transactional(readOnly = true)
    public BatchRun get(Long batchRunId) {
        return getBatchRun(batchRunId);
    }

    @Transactional
    public void complete(Long batchRunId) {
        getBatchRun(batchRunId).complete(LocalDateTime.now(clock));
    }

    @Transactional
    public void fail(Long batchRunId, String errorMessage) {
        getBatchRun(batchRunId).fail(LocalDateTime.now(clock), errorMessage);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findLastCompletedAt() {
        return batchRunRepository.findFirstByStatusOrderByFinishedAtDesc(BatchRunStatus.COMPLETED)
            .map(BatchRun::getFinishedAt);
    }

    private BatchRun getBatchRun(Long batchRunId) {
        return batchRunRepository.findById(batchRunId)
            .orElseThrow(() -> new BusinessException(BatchErrorCode.BATCH_RUN_NOT_FOUND));
    }
}
