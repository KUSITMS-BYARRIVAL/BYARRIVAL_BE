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
        assertThat(AiTextMatcher.normalize("“기준금리는  연 3.0%다.”")).isEqualTo("기준금리는연30다");
    }
}
