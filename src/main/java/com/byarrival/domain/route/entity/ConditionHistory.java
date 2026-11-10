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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 고른 컨디션 이력. 추후 학습 기반 개인화의 기초 데이터다.
 */
@Entity
@Table(name = "condition_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConditionHistory extends BaseEntity {

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
    private Long routeId;

    public static ConditionHistory of(Route route) {
        ConditionHistory history = new ConditionHistory();
        history.userId = route.getUserId();
        history.serviceDate = route.getServiceDate();
        history.condition = route.getCondition();
        history.routeId = route.getId();
        return history;
    }
}
