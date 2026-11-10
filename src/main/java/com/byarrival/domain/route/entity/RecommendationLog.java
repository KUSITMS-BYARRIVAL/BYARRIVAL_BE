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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 노선 추천 로그. 생성 시 입력 조건·후보 수·폴백 단계·선택 결과를, 시작하기 시 담은 카드를 남긴다.
 * 선택 결과가 0건이면 노선 없이(route_id NULL) 남긴다.
 */
@Entity
@Table(name = "recommendation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long routeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_condition", nullable = false, length = 10)
    private RouteCondition condition;

    @Column(nullable = false)
    private int targetMinutes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "bigint[]")
    private List<Long> interestCategoryIds;

    @Column(nullable = false)
    private int candidateCount;

    @Column(nullable = false)
    private int candidateHardCount;

    @Column(nullable = false)
    private int candidateEasyCount;

    private Integer fallbackStep;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "bigint[]")
    private List<Long> selectedArticleIds;

    @Column(nullable = false)
    private int selectedHardCount;

    @Column(nullable = false)
    private int selectedEasyCount;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal selectedMinutes;

    @Column(nullable = false)
    private int poolSize;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "bigint[]")
    private List<Long> cartArticleIds;

    @Column(precision = 8, scale = 3)
    private BigDecimal cartMinutes;

    private LocalDateTime boardedAt;

    @Builder
    private RecommendationLog(Long routeId, Long userId, LocalDate serviceDate, RouteCondition condition,
        int targetMinutes, List<Long> interestCategoryIds, int candidateCount, int candidateHardCount,
        int candidateEasyCount, Integer fallbackStep, List<Long> selectedArticleIds, int selectedHardCount,
        int selectedEasyCount, BigDecimal selectedMinutes, int poolSize) {
        this.routeId = routeId;
        this.userId = userId;
        this.serviceDate = serviceDate;
        this.condition = condition;
        this.targetMinutes = targetMinutes;
        this.interestCategoryIds = List.copyOf(interestCategoryIds);
        this.candidateCount = candidateCount;
        this.candidateHardCount = candidateHardCount;
        this.candidateEasyCount = candidateEasyCount;
        this.fallbackStep = fallbackStep;
        this.selectedArticleIds = List.copyOf(selectedArticleIds);
        this.selectedHardCount = selectedHardCount;
        this.selectedEasyCount = selectedEasyCount;
        this.selectedMinutes = selectedMinutes;
        this.poolSize = poolSize;
    }

    /**
     * 시작하기 시점에 담은 카드와 합계 시간을 남긴다. 보딩패스에서 돌아가 다시 시작하면 마지막 값으로 덮어쓴다.
     */
    public void recordBoarding(List<Long> cartArticleIds, BigDecimal cartMinutes, LocalDateTime boardedAt) {
        this.cartArticleIds = List.copyOf(cartArticleIds);
        this.cartMinutes = cartMinutes;
        this.boardedAt = boardedAt;
    }
}
