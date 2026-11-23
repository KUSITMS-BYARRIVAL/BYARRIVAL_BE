package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.entity.AdminMemberAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminMemberAccessLogRepository extends JpaRepository<AdminMemberAccessLog, Long> {
}
