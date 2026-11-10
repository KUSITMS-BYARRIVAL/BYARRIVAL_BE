package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserIdAndStatus(
        SocialProvider provider, String providerUserId, UserStatus status);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    boolean existsByIdAndStatus(Long id, UserStatus status);

    /**
     * 같은 소셜 계정으로 since 이후에 탈퇴한 회원 중 가장 최근에 탈퇴한 회원
     */
    Optional<User> findFirstByProviderAndProviderUserIdAndStatusAndWithdrawnAtGreaterThanEqualOrderByWithdrawnAtDesc(
        SocialProvider provider, String providerUserId, UserStatus status, LocalDateTime since);

    /**
     * 보관 기간이 지난 탈퇴 회원을 지운다. 회원의 다른 데이터는 DB의 ON DELETE CASCADE로 함께 지워진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.status = :status AND u.withdrawnAt < :before")
    int deleteByStatusAndWithdrawnAtBefore(@Param("status") UserStatus status, @Param("before") LocalDateTime before);
}
