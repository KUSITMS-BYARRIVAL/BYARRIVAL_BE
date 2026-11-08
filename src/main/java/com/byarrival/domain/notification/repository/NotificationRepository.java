package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.entity.PushStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
        Long userId, LocalDateTime since, Limit limit);

    List<Notification> findByUserIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
        Long userId, Long cursor, LocalDateTime since, Limit limit);

    long countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTypeAndServiceDate(Long userId, NotificationType type, LocalDate serviceDate);

    long countByUserIdAndServiceDateAndTypeIn(Long userId, LocalDate serviceDate, Collection<NotificationType> types);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now, n.updatedAt = :now "
        + "WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.pushStatus = :status, n.updatedAt = :now WHERE n.id = :id")
    int updatePushStatus(@Param("id") Long id, @Param("status") PushStatus status, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
