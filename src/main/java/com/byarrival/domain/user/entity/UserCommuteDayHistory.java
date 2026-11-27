package com.byarrival.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이동 요일 변경 이력(HOME-02 스트릭 기준). effectiveFrom 서비스일부터 이 요일들이 스트릭 대상일이다.
 */
@Entity
@Table(name = "user_commute_day_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCommuteDayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private short commuteDays;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private UserCommuteDayHistory(Long userId, LocalDate effectiveFrom, CommuteDays days, LocalDateTime now) {
        this.userId = userId;
        this.effectiveFrom = effectiveFrom;
        this.commuteDays = (short) days.bits();
        this.createdAt = now;
    }

    public static UserCommuteDayHistory of(Long userId, LocalDate effectiveFrom, CommuteDays days,
        LocalDateTime now) {
        return new UserCommuteDayHistory(userId, effectiveFrom, days, now);
    }

    public CommuteDays days() {
        return new CommuteDays(commuteDays);
    }

    /**
     * 같은 서비스일에 다시 바꾸면 마지막으로 바꾼 요일로 덮어쓴다.
     */
    public void change(CommuteDays days, LocalDateTime now) {
        this.commuteDays = (short) days.bits();
        this.createdAt = now;
    }
}
