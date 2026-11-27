package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.UserCommuteDayHistory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCommuteDayHistoryRepository extends JpaRepository<UserCommuteDayHistory, Long> {

    boolean existsByUserId(Long userId);

    Optional<UserCommuteDayHistory> findByUserIdAndEffectiveFrom(Long userId, LocalDate effectiveFrom);

    List<UserCommuteDayHistory> findAllByUserIdIn(Collection<Long> userIds);
}
