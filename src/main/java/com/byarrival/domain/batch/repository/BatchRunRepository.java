package com.byarrival.domain.batch.repository;

import com.byarrival.domain.batch.entity.BatchRun;
import com.byarrival.domain.batch.entity.BatchRunStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    boolean existsByServiceDateAndStatusIn(LocalDate serviceDate, Collection<BatchRunStatus> statuses);

    Optional<BatchRun> findFirstByStatusOrderByFinishedAtDesc(BatchRunStatus status);

    Optional<BatchRun> findFirstByServiceDateOrderByIdDesc(LocalDate serviceDate);

    List<BatchRun> findByServiceDateAndStatus(LocalDate serviceDate, BatchRunStatus status);
}
