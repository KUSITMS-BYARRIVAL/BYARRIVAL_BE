package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserIdAndStatus(
        SocialProvider provider, String providerUserId, UserStatus status);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    // 같은 회원의 설정 변경을 줄 세운다. users 행 전체를 UPDATE하므로 겹치면 먼저 저장한 값이 덮어써진다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.status = :status")
    Optional<User> findForUpdate(@Param("id") Long id, @Param("status") UserStatus status);

    boolean existsByIdAndStatus(Long id, UserStatus status);
}
