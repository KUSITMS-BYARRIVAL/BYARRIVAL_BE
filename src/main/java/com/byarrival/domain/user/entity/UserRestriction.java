package com.byarrival.domain.user.entity;

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
 * 회원 이용 제한 조치(MEMBER-03). 제한한 내용은 바꾸지 않고, 운영자가 먼저 풀 때만 해제 시각을 남긴다.
 * 끝나는 시각이 지나면 해제 기록 없이 풀린 것으로 본다.
 */
@Entity
@Table(name = "user_restriction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestrictionPeriod period;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private LocalDateTime releasedAt;

    private Long releasedByAdminId;

    private UserRestriction(Long userId, Long adminId, RestrictionPeriod period, String reason,
        LocalDateTime startsAt) {
        this.userId = userId;
        this.adminId = adminId;
        this.period = period;
        this.reason = reason;
        this.startsAt = startsAt;
        this.endsAt = period.endsAt(startsAt);
    }

    public static UserRestriction start(Long userId, Long adminId, RestrictionPeriod period, String reason,
        LocalDateTime now) {
        return new UserRestriction(userId, adminId, period, reason, now);
    }

    public boolean isActiveAt(LocalDateTime now) {
        return releasedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }

    public void release(Long adminId, LocalDateTime now) {
        this.releasedAt = now;
        this.releasedByAdminId = adminId;
    }
}
