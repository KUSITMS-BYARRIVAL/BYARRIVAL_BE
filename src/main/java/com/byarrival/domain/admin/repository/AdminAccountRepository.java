package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.entity.AdminAccount;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByEmail(String email);

    Optional<AdminAccount> findByIdAndActiveTrue(Long id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE admin_account SET token_version = token_version + 1, updated_at = :now WHERE id = :id",
        nativeQuery = true)
    int increaseTokenVersion(@Param("id") Long id, @Param("now") LocalDateTime now);
}
