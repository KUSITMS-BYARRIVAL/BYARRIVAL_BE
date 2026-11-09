package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.NotificationCategory;
import com.byarrival.domain.notification.entity.UserNotificationSetting;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, Long> {

    List<UserNotificationSetting> findByUserId(Long userId);

    Optional<UserNotificationSetting> findByUserIdAndCategory(Long userId, NotificationCategory category);

    /**
     * 같은 카테고리 변경이 동시에 와도 유니크 제약 위반 없이 마지막 값으로 저장한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO user_notification_setting (user_id, category, enabled, created_at, updated_at) "
        + "VALUES (:userId, :category, :enabled, :now, :now) "
        + "ON CONFLICT (user_id, category) DO UPDATE SET enabled = EXCLUDED.enabled, "
        + "updated_at = EXCLUDED.updated_at", nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("category") String category, @Param("enabled") boolean enabled,
        @Param("now") LocalDateTime now);
}
