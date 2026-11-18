package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 자동 알림을 받을 수 있는 회원: 활동 중이고 온보딩을 마친 회원
     */
    @Query("SELECT u.id FROM User u WHERE u.status = com.byarrival.domain.user.entity.UserStatus.ACTIVE "
        + "AND u.onboardingStatus = com.byarrival.domain.user.entity.OnboardingStatus.COMPLETED ORDER BY u.id")
    List<Long> findNotifiableUserIds();

    /**
     * 같은 소셜 계정으로 since 이후에 탈퇴한 회원 중 가장 최근에 탈퇴한 회원
     */
    Optional<User> findFirstByProviderAndProviderUserIdAndStatusAndWithdrawnAtGreaterThanEqualOrderByWithdrawnAtDesc(
        SocialProvider provider, String providerUserId, UserStatus status, LocalDateTime since);

    /**
     * 보관 기간이 지난 탈퇴 회원을 지운다. 회원의 다른 데이터는 DB의 ON DELETE CASCADE로 함께 지워진다.
     */
    // 파기 전에 행을 잠가 그사이 재가입 복원과 겹치지 않게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.withdrawnAt < :before")
    List<User> findAllForPurge(@Param("status") UserStatus status, @Param("before") LocalDateTime before);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.status = :status AND u.withdrawnAt < :before")
    int deleteByStatusAndWithdrawnAtBefore(@Param("status") UserStatus status, @Param("before") LocalDateTime before);
}
