package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.UserJobGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJobGroupRepository extends JpaRepository<UserJobGroup, Long> {

    @Query("SELECT u.jobGroup FROM UserJobGroup u WHERE u.userId = :userId ORDER BY u.sortOrder")
    List<JobGroup> findJobGroupsByUserId(@Param("userId") Long userId);

    // 바로 실행해야 같은 직군·순서를 다시 넣을 때 유니크 제약에 걸리지 않는다(Hibernate는 insert를 delete보다 먼저 보낸다).
    @Modifying
    @Query("DELETE FROM UserJobGroup u WHERE u.userId = :userId")
    void deleteAllByUserIdInBatch(@Param("userId") Long userId);
}
