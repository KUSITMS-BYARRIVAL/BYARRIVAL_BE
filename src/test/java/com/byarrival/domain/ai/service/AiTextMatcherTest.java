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

    @Test
    void 원문의_일부만_옮긴_인용은_인정한다() {
        assertThat(AiTextMatcher.containsQuote("한국은행은 기준금리를 연 3.0%로 동결했다.", "기준금리를 연 3.0%로")).isTrue();
        assertThat(AiTextMatcher.containsQuote("성장률은 -0.5%로 유지됐다.", "-0.5%로 유지됐다")).isTrue();
    }

    @Test
    void 숫자의_부호나_자릿수를_잘라_낸_인용은_인정하지_않는다() {
        assertThat(AiTextMatcher.containsQuote("기준금리는 -0.5%로 유지됐습니다.", "0.5%로 유지됐습니다.")).isFalse();
        assertThat(AiTextMatcher.containsQuote("수출이 130% 늘었다.", "30% 늘었다")).isFalse();
        assertThat(AiTextMatcher.containsQuote("금리는 3.5%다.", "금리는 3")).isFalse();
        assertThat(AiTextMatcher.containsQuote("총 1,200명이 참여했다.", "200명이 참여했다")).isFalse();
    }

    @Test
    void 같은_구절이_여러_번_나오면_숫자_경계가_맞는_곳이_하나만_있어도_인정한다() {
        assertThat(AiTextMatcher.containsQuote("-0.5%로 내렸다가 0.5%로 올랐다.", "0.5%로 올랐다")).isTrue();
        assertThat(AiTextMatcher.containsQuote("130%와 30% 증가", "30% 증가")).isTrue();
    }

    @Test
    void 숫자_뒤에_문장_부호와_다른_숫자가_이어져도_원문_구절이면_인정한다() {
        // 정규화하면 "30%. 2026"이 "302026"으로 붙지만, 원문에서는 30 뒤가 %라 숫자가 끝난 자리다
        assertThat(AiTextMatcher.containsQuote("올해 수출 증가율은 30%. 2026년 전망은 밝다.", "올해 수출 증가율은 30%")).isTrue();
        assertThat(AiTextMatcher.containsQuote("목표는 3. 4분기에 달성한다.", "목표는 3")).isTrue();
        assertThat(AiTextMatcher.containsQuote("총 -5, 10도 차이", "10도 차이")).isTrue();
    }

    @Test
    void 숫자_끝에_소수점이_붙어도_뒤에_숫자가_없으면_숫자가_끝난_자리다() {
        assertThat(AiTextMatcher.containsQuote("물가는 3.", "물가는 3")).isTrue();
        assertThat(AiTextMatcher.containsQuote("물가는 3.2%다", "물가는 3")).isFalse();
    }
}
