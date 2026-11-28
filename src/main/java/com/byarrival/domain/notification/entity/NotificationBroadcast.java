package com.byarrival.domain.notification.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어드민 알림 발송 한 건(NOTI-01). 즉시 발송은 지금 시각으로 예약한 것과 같다.
 * 발송을 시작하기 전(SCHEDULED)에만 수정·취소할 수 있다.
 */
@Entity
@Table(name = "notification_broadcast")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationBroadcast extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BroadcastKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BroadcastTarget target;

    @Column(nullable = false, length = 20)
    private String title;

    @Column(nullable = false, length = 60)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationScreen screen;

    @Column(length = 500)
    private String url;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BroadcastStatus status;

    private Integer recipientCount;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int failureCount;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private NotificationBroadcast(Long adminId, BroadcastContent content, LocalDateTime scheduledAt) {
        this.adminId = adminId;
        apply(content, scheduledAt);
        this.status = BroadcastStatus.SCHEDULED;
    }

    public static NotificationBroadcast schedule(Long adminId, BroadcastContent content, LocalDateTime scheduledAt) {
        return new NotificationBroadcast(adminId, content, scheduledAt);
    }

    public boolean isEditable(LocalDateTime now) {
        return status == BroadcastStatus.SCHEDULED && scheduledAt.isAfter(now);
    }

    public void edit(BroadcastContent content, LocalDateTime scheduledAt) {
        apply(content, scheduledAt);
    }

    public void cancel() {
        this.status = BroadcastStatus.CANCELED;
    }

    public BroadcastContent content() {
        return new BroadcastContent(kind, target, title, body, screen, url);
    }

    private void apply(BroadcastContent content, LocalDateTime scheduledAt) {
        this.kind = content.kind();
        this.target = content.target();
        this.title = content.title();
        this.body = content.body();
        this.screen = content.screen();
        this.url = content.url();
        this.scheduledAt = scheduledAt;
    }

    /**
     * @param url 이동 화면이 공지사항(NOTICE)일 때만 있다
     */
    public record BroadcastContent(BroadcastKind kind, BroadcastTarget target, String title, String body,
                                   NotificationScreen screen, String url) {
    }
}
