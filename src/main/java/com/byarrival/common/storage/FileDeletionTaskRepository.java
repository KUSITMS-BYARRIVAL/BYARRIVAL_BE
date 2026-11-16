package com.byarrival.common.storage;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionTaskRepository extends JpaRepository<FileDeletionTask, Long> {

    List<FileDeletionTask> findAllByOrderByIdAsc(Pageable pageable);
}
