package com.byarrival.common.storage;

import com.byarrival.common.monitoring.ServerErrorNotifier;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저장소 파일 삭제를 작업으로 남기고 처리한다.
 * DB 변경과 같은 트랜잭션에서 작업을 남기므로, 삭제 대상을 잃지 않는다. 실제 삭제는 외부 호출이라 트랜잭션 밖에서 한다.
 */
@Slf4j
@Service
public class FileDeletionService {

    static final int BATCH_SIZE = 100;
    // 이만큼 실패하면 운영자가 확인하도록 서버 에러 채널에 한 번 알린다. 그 뒤에도 계속 다시 시도한다
    static final int ALERT_ATTEMPTS = 5;
    static final String ALERT_METHOD = "SCHEDULED";
    static final String ALERT_URI = "file-deletion";

    private final FileDeletionTaskRepository repository;
    private final FileStorage fileStorage;
    private final ServerErrorNotifier serverErrorNotifier;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public FileDeletionService(FileDeletionTaskRepository repository, FileStorage fileStorage,
        ServerErrorNotifier serverErrorNotifier, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.serverErrorNotifier = serverErrorNotifier;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 호출하는 쪽의 트랜잭션 안에서 작업을 남긴다. 그 트랜잭션이 롤백되면 작업도 남지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Collection<String> urls) {
        repository.saveAll(urls.stream().map(FileDeletionTask::of).toList());
    }

    /**
     * 트랜잭션 밖에서 생긴 파일(예: 저장에 실패한 요청이 올린 파일)을 지우도록 새 트랜잭션으로 작업을 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueNow(String url) {
        repository.save(FileDeletionTask.of(url));
    }

    /**
     * 남은 작업을 실패 횟수가 적은 순, 그다음 오래된 순으로 처리한다. 지운 작업은 없애고, 실패한 작업은 실패 횟수를 늘려 남긴다.
     * 저장소 설정이 없으면 실제로 지울 수 없으므로 작업을 건드리지 않고 남겨 둔다.
     * 블루/그린 전환 중 두 서버가 같은 작업을 처리해도, 같은 파일을 두 번 지우는 것뿐이라 결과는 같다.
     *
     * @return 지운 파일 수
     */
    public int deletePending() {
        if (!fileStorage.isAvailable()) {
            return 0;
        }
        List<FileDeletionTask> tasks = repository.findAllByOrderByAttemptsAscIdAsc(PageRequest.of(0, BATCH_SIZE));
        int deleted = 0;
        for (FileDeletionTask task : tasks) {
            try {
                fileStorage.delete(task.getUrl());
            } catch (RuntimeException e) {
                recordFailure(task.getId(), e);
                continue;
            }
            repository.deleteById(task.getId());
            deleted++;
        }
        return deleted;
    }

    private void recordFailure(Long taskId, RuntimeException error) {
        Integer attempts = transactionTemplate.execute(status -> repository.findById(taskId)
            .map(task -> {
                task.recordFailure(LocalDateTime.now(clock));
                return task.getAttempts();
            })
            .orElse(null));
        log.warn("[FileDeletion] 파일 삭제 실패, 다음 주기에 다시 시도: taskId={}, attempts={}, error={}", taskId, attempts,
            error.getClass().getSimpleName());
        if (attempts != null && attempts == ALERT_ATTEMPTS) {
            serverErrorNotifier.notify(ALERT_METHOD, ALERT_URI, error);
        }
    }
}
