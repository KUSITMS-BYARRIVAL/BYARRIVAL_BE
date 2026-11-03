package com.byarrival.domain.auth.repository;

import com.byarrival.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndUserId(String tokenHash, Long userId);

    /**
     * 아직 폐기되지 않은 토큰만 폐기한다. 같은 토큰으로 요청이 동시에 와도 한 요청만 1을 돌려받는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt, t.updatedAt = :revokedAt "
        + "WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);
}
