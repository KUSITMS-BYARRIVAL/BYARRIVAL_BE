package com.byarrival.domain.route.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.domain.route.config.RouteProperties.DifficultyRatio;
import com.byarrival.domain.route.entity.RouteCondition;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutePropertiesTest {

    private static final Map<RouteCondition, DifficultyRatio> RATIOS = Map.of(
        RouteCondition.SUNNY, new DifficultyRatio(3, 2),
        RouteCondition.NORMAL, new DifficultyRatio(2, 3),
        RouteCondition.CLOUDY, new DifficultyRatio(1, 4),
        RouteCondition.RAINY, new DifficultyRatio(1, 4));

    @Test
    void 컨디션_비율이_빠지면_시작하지_않는다() {
        Map<RouteCondition, DifficultyRatio> missing = Map.of(RouteCondition.SUNNY, new DifficultyRatio(3, 2));

        assertThatThrownBy(() -> new RouteProperties(new BigDecimal("0.7"), new BigDecimal("1.2"), 2, missing))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("route.difficulty-ratios");
    }

    @Test
    void 허용_범위가_뒤집히면_시작하지_않는다() {
        assertThatThrownBy(() -> new RouteProperties(new BigDecimal("1.2"), new BigDecimal("0.7"), 2, RATIOS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 풀_배수는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new RouteProperties(new BigDecimal("0.7"), new BigDecimal("1.2"), 0, RATIOS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정상_설정이면_컨디션별_비율을_돌려준다() {
        RouteProperties properties = new RouteProperties(new BigDecimal("0.7"), new BigDecimal("1.2"), 2, RATIOS);

        assertThat(properties.ratioOf(RouteCondition.CLOUDY)).isEqualTo(new DifficultyRatio(1, 4));
    }

    @Test
    void 최소_시간은_올림하고_최대_시간은_내림한다() {
        RouteProperties properties = new RouteProperties(new BigDecimal("0.7"), new BigDecimal("1.2"), 2, RATIOS);

        assertThat(properties.minMinutes(11)).isEqualTo(8);
        assertThat(properties.maxMinutes(11)).isEqualTo(13);
        assertThat(properties.minMinutes(10)).isEqualTo(7);
        assertThat(properties.maxMinutes(10)).isEqualTo(12);
    }
}
