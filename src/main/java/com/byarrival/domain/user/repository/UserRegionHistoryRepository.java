package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.UserRegionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRegionHistoryRepository extends JpaRepository<UserRegionHistory, Long> {

    List<UserRegionHistory> findAllByUserIdOrderByIdAsc(Long userId);
}
