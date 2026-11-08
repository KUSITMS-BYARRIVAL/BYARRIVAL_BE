package com.byarrival.domain.notification.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림함의 알림 하나. 푸시 발송 여부와 상관없이 보낸 알림은 모두 저장한다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Convert(converter = NotificationParamsConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private Map<String, String> params;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushStatus pushStatus;

    private LocalDateTime readAt;

    private Notification(Long userId, NotificationType type, String title, String body, Map<String, String> params,
        LocalDate serviceDate, PushStatus pushStatus) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.params = Map.copyOf(params);
        this.serviceDate = serviceDate;
        this.pushStatus = pushStatus;
    }

    public static Notification create(Long userId, NotificationType type, String title, String body,
        Map<String, String> params, LocalDate serviceDate, boolean push) {
        return new Notification(userId, type, title, body, params, serviceDate,
            push ? PushStatus.PENDING : PushStatus.SKIPPED);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
