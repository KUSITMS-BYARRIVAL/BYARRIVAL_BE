package com.byarrival.domain.route.config;

import com.byarrival.domain.route.entity.RouteCondition;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 노선 추천 설정값. 허용 시간 범위(T×최소 비율 ~ T×최대 비율), 후보 카드 풀 배수, 컨디션별 HARD:EASY 비율(기사 수 기준).
 */
@ConfigurationProperties(prefix = "route")
public record RouteProperties(
    BigDecimal minTimeRatio,
    BigDecimal maxTimeRatio,
    int poolMultiplier,
    Map<RouteCondition, DifficultyRatio> difficultyRatios
) {

    public RouteProperties {
        if (minTimeRatio == null || maxTimeRatio == null) {
            throw new IllegalArgumentException("route.min-time-ratio와 route.max-time-ratio는 필수입니다.");
        }
        if (minTimeRatio.signum() <= 0 || minTimeRatio.compareTo(BigDecimal.ONE) > 0
            || maxTimeRatio.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("route.min-time-ratio는 0 초과 1 이하, route.max-time-ratio는 1 이상이어야 합니다.");
        }
        if (poolMultiplier < 1) {
            throw new IllegalArgumentException("route.pool-multiplier는 1 이상이어야 합니다.");
        }
        if (difficultyRatios == null) {
            throw new IllegalArgumentException("route.difficulty-ratios는 필수입니다.");
        }
        for (RouteCondition condition : RouteCondition.values()) {
            DifficultyRatio ratio = difficultyRatios.get(condition);
            if (ratio == null) {
                throw new IllegalArgumentException("route.difficulty-ratios." + condition + " 설정이 없습니다.");
            }
            if (ratio.hard() < 0 || ratio.easy() < 0 || ratio.hard() + ratio.easy() == 0) {
                throw new IllegalArgumentException("route.difficulty-ratios." + condition + "의 hard·easy는 0 이상이고 합이 0보다 커야 합니다.");
            }
        }
        difficultyRatios = new EnumMap<>(difficultyRatios);
    }

    public DifficultyRatio ratioOf(RouteCondition condition) {
        return difficultyRatios.get(condition);
    }

    public record DifficultyRatio(int hard, int easy) {
    }
}
