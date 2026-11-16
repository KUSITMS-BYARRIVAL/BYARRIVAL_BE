package com.byarrival.common.storage;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionTaskRepository extends JpaRepository<FileDeletionTask, Long> {

    // 실패 횟수가 적은 작업부터 가져와, 계속 실패하는 작업이 배치를 차지하지 않게 한다
    List<FileDeletionTask> findAllByOrderByAttemptsAscIdAsc(Pageable pageable);
}
