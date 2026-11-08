package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.DeviceToken;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * 처음 보는 토큰이면 등록하고, 이미 있으면 지금 사용자와 플랫폼으로 바꾼다.
     * 한 기기에서 다른 계정으로 로그인하면 토큰이 새 계정으로 옮겨진다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO device_token (user_id, token, platform, created_at, updated_at) "
        + "VALUES (:userId, :token, :platform, :now, :now) "
        + "ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, "
        + "updated_at = EXCLUDED.updated_at", nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("token") String token, @Param("platform") String platform,
        @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DeviceToken d WHERE d.token = :token AND d.userId = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DeviceToken d WHERE d.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
