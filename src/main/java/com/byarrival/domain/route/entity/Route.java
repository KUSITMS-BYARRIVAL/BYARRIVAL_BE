package com.byarrival.domain.route.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스일당 하나만 만드는 오늘의 노선. 후보 카드 풀은 {@link RouteCard}에 스냅샷으로 둔다.
 */
@Entity
@Table(name = "route")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_condition", nullable = false, length = 10)
    private RouteCondition condition;

    @Column(nullable = false)
    private int targetMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status;

    @Column(nullable = false)
    private int fallbackStep;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime boardedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Builder
    private Route(Long userId, LocalDate serviceDate, RouteCondition condition, int targetMinutes, int fallbackStep,
        LocalDateTime expiresAt) {
        this.userId = userId;
        this.serviceDate = serviceDate;
        this.condition = condition;
        this.targetMinutes = targetMinutes;
        this.fallbackStep = fallbackStep;
        this.expiresAt = expiresAt;
        this.status = RouteStatus.CREATED;
    }

    public boolean isExpired(LocalDateTime now) {
        return status != RouteStatus.COMPLETED && !now.isBefore(expiresAt);
    }

    /**
     * 현재 시각 기준 상태. 완주하지 않은 채 만료 시각을 넘기면 EXPIRED로 본다.
     */
    public RouteStatus statusAt(LocalDateTime now) {
        return isExpired(now) ? RouteStatus.EXPIRED : status;
    }

    /**
     * 시작하기: 담은 카드를 저장하고 보딩패스 단계로 넘어간다.
     */
    public void board(LocalDateTime now) {
        this.status = RouteStatus.BOARDING;
        this.boardedAt = now;
    }

    /**
     * 보딩패스에서 뒤로 가기: 담은 카드는 그대로 두고 카드 선택 단계로 돌아간다.
     */
    public void returnToCardSelection() {
        this.status = RouteStatus.CREATED;
    }
}
