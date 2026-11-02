package com.byarrival.domain.batch.repository;

import com.byarrival.domain.batch.entity.BatchRun;
import com.byarrival.domain.batch.entity.BatchRunStatus;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    boolean existsByServiceDateAndStatusIn(LocalDate serviceDate, Collection<BatchRunStatus> statuses);
}
