package com.byarrival.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiTextMatcherTest {

    @Test
    void 자릿수_쉼표와_소수점_끝_0은_같은_값으로_본다() {
        assertThat(AiTextMatcher.numbersOf("1,335.70원, 3.0%, 2029년"))
            .containsExactlyInAnyOrder("1335.7", "3", "2029");
    }

    @Test
    void 자릿수_쉼표가_아닌_쉼표는_숫자를_나눈다() {
        assertThat(AiTextMatcher.numbersOf("1,2위를 다퉜다")).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void 숫자_끝의_마침표와_쉼표는_숫자가_아니다() {
        assertThat(AiTextMatcher.numbersOf("28명, 28.5...")).containsExactlyInAnyOrder("28", "28.5");
    }

    @Test
    void 비교할_때_공백과_문장부호를_뺀다() {
        assertThat(AiTextMatcher.normalize("“기준금리는  연 3.0%다.”")).isEqualTo("기준금리는연3.0다");
    }

    @Test
    void 음수_부호를_값에_남긴다() {
        assertThat(AiTextMatcher.numbersOf("성장률은 -0.5%, 전월은 −1.2%였다")).containsExactlyInAnyOrder("-0.5", "-1.2");
        assertThat(AiTextMatcher.numbersOf("성장률은 0.5%였다")).containsExactly("0.5");
    }

    @Test
    void 날짜와_점수의_하이픈은_음수_부호가_아니다() {
        assertThat(AiTextMatcher.numbersOf("2026-11-17 경기는 3-1로 끝났다")).containsExactlyInAnyOrder("2026", "11", "17", "3", "1");
        assertThat(AiTextMatcher.numbersOf("A-1 구역")).containsExactly("1");
    }

    @Test
    void 비교할_때_숫자의_소수점과_음수_부호는_남긴다() {
        assertThat(AiTextMatcher.normalize("기준금리는 연 3.0%다")).isNotEqualTo(AiTextMatcher.normalize("기준금리는 연 30%다"));
        assertThat(AiTextMatcher.normalize("성장률은 -0.5%다")).isEqualTo("성장률은-0.5다");
        assertThat(AiTextMatcher.normalize("1,000명, 2026-11-17")).isEqualTo("1,000명20261117");
    }
}
