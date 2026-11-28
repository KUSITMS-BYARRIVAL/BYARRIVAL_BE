package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.entity.AdminMemberAccessLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminMemberAccessLogRepository extends JpaRepository<AdminMemberAccessLog, Long> {

    @Modifying
    @Query(value = "DELETE FROM admin_member_access_log WHERE accessed_at < :before", nativeQuery = true)
    int deleteAccessedBefore(@Param("before") LocalDateTime before);
}
