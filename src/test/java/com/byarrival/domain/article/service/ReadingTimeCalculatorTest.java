package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.domain.article.config.ArticleReadingProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReadingTimeCalculatorTest {

    private final ReadingTimeCalculator calculator = new ReadingTimeCalculator(new ArticleReadingProperties(500));

    @Test
    void 글자수를_분당_500자로_나눈다() {
        assertThat(calculator.calculate("가".repeat(1000))).isEqualByComparingTo("2");
        assertThat(calculator.calculate("가".repeat(1234))).isEqualTo(new BigDecimal("2.468"));
    }

    @Test
    void 공백과_줄바꿈도_글자수에_넣는다() {
        assertThat(calculator.calculate("가 나\n다")).isEqualTo(new BigDecimal("0.010"));
    }

    @Test
    void 소수_셋째_자리_아래는_반올림하지_않고_버린다() {
        ReadingTimeCalculator perThree = new ReadingTimeCalculator(new ArticleReadingProperties(3));

        assertThat(perThree.calculate("가나")).isEqualTo(new BigDecimal("0.666"));
    }

    @Test
    void 이모지처럼_두_칸을_쓰는_문자도_한_글자로_센다() {
        assertThat(calculator.calculate("😀")).isEqualTo(new BigDecimal("0.002"));
    }

    @Test
    void 읽기_속도는_0보다_커야_한다() {
        assertThatThrownBy(() -> new ArticleReadingProperties(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
