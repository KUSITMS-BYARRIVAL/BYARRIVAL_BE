package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.NotificationBroadcast;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, Long> {

    // 수정·취소가 발송 시작과 겹치지 않게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM NotificationBroadcast b WHERE b.id = :id")
    Optional<NotificationBroadcast> findForUpdate(@Param("id") Long id);

    @Query("SELECT b.id FROM NotificationBroadcast b WHERE b.status = "
        + "com.byarrival.domain.notification.entity.BroadcastStatus.SCHEDULED AND b.scheduledAt <= :now "
        + "ORDER BY b.scheduledAt, b.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now);

    /**
     * 예약 시각이 된 발송을 시작 상태로 바꾼다. 두 서버가 함께 돌아도 한쪽만 바꾼다.
     *
     * @return 바꿨으면 1
     */
    @Modifying
    @Query(value = "UPDATE notification_broadcast SET status = 'SENDING', started_at = :now, updated_at = :now "
        + "WHERE id = :id AND status = 'SCHEDULED' AND scheduled_at <= :now", nativeQuery = true)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "UPDATE notification_broadcast SET status = :status, recipient_count = :recipients, "
        + "completed_at = :now, updated_at = :now WHERE id = :id", nativeQuery = true)
    int finish(@Param("id") Long id, @Param("status") String status, @Param("recipients") int recipients,
        @Param("now") LocalDateTime now);

    // 푸시는 트랜잭션 밖에서 결과를 남기므로 이 쿼리만의 트랜잭션으로 반영한다
    @Transactional
    @Modifying
    @Query(value = "UPDATE notification_broadcast SET success_count = success_count + :success, "
        + "failure_count = failure_count + :failure WHERE id = :id", nativeQuery = true)
    int addResult(@Param("id") Long id, @Param("success") int success, @Param("failure") int failure);

    @Query("SELECT b FROM NotificationBroadcast b WHERE b.scheduledAt >= :from AND b.scheduledAt < :to "
        + "ORDER BY b.scheduledAt DESC, b.id DESC")
    List<NotificationBroadcast> findScheduledBetween(@Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}
