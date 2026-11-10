package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArticleSummaryValidatorTest {

    private static final String TITLE = "한은, 기준금리 연 3.0%로 동결";
    private static final String ONE_LINE = "한국은행이 기준금리를 동결했습니다.";
    private static final List<String> KEYWORDS = List.of("기준금리", "한국은행", "물가");

    private final ArticleSummaryValidator validator = new ArticleSummaryValidator();

    @Test
    void 제목과_난이도가_같고_30자_이내_3문장이면_통과한다() {
        ValidatedSummary summary = validator.validate(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY,
                " " + "나".repeat(37) + "니다. ", List.of("가".repeat(27) + "니다.", " 둘째 문장입니다. ", "셋째 문장입니다"),
                List.of(" 기준금리 ", "나".repeat(10), "물가 상승")),
            TITLE, ArticleDifficulty.EASY);

        assertThat(summary.oneLine()).isEqualTo("나".repeat(37) + "니다.");
        assertThat(summary.sentences()).containsExactly("가".repeat(27) + "니다.", "둘째 문장입니다.", "셋째 문장입니다");
        assertThat(summary.keywords()).containsExactly("기준금리", "나".repeat(10), "물가 상승");
    }

    @Test
    void 키워드가_3개가_아니거나_10자를_넘거나_중복되거나_샵이_붙으면_실패한다() {
        assertInvalid(withKeywords(List.of("기준금리", "한국은행")));
        assertInvalid(withKeywords(List.of("기준금리", "한국은행", "물가", "환율")));
        assertInvalid(withKeywords(null));
        assertInvalid(withKeywords(List.of("기준금리", "한국은행", "나".repeat(11))));
        assertInvalid(withKeywords(List.of("기준금리", "한국은행", " ")));
        assertInvalid(withKeywords(List.of("기준금리", "한국은행", " 기준금리")));
        assertInvalid(withKeywords(List.of("기준금리", "한국은행", "#물가")));
    }

    private void assertInvalid(ArticleSummaryOutput output) {
        assertInvalid(output, TITLE, ArticleDifficulty.EASY);
    }

    private static ArticleSummaryOutput withKeywords(List<String> keywords) {
        return new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE, List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."),
            keywords);
    }

    @Test
    void 문장이_니다로_끝나지_않으면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "금리를 동결했다.", "둘째 문장입니다.", "셋째 문장입니다."),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "시총 258조원 증발", "둘째 문장입니다.", "셋째 문장입니다."),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "한은 기준금리 동결",
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), KEYWORDS), TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 제목을_그대로_반복하면_실패한다() {
        String title = "환율이 12원 내렸습니다";
        assertInvalid(new ArticleSummaryOutput(title, ArticleDifficulty.EASY, title,
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), KEYWORDS), title, ArticleDifficulty.EASY);
    }

    @Test
    void 어긴_규칙을_모두_모아_다시_요청할_때_쓸_수_있게_알려준다() {
        String longSentence = "가".repeat(36) + "니다.";

        assertThatThrownBy(() -> validator.validate(
            output(TITLE, ArticleDifficulty.EASY, "첫째 문장입니다.", longSentence, "셋째 문장이다."),
            TITLE, ArticleDifficulty.EASY))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("summary 2번 문장이 39자입니다")
            .hasMessageContaining(longSentence)
            .hasMessageContaining("summary 3번 문장을");
    }

    @Test
    void 한_줄_요약이_40자를_넘거나_비어_있으면_실패한다() {
        List<String> sentences = List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다.");
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "나".repeat(39) + "니다", sentences, KEYWORDS),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, " ", sentences, KEYWORDS),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, null, sentences, KEYWORDS),
            TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 공백을_포함해_30자를_넘는_문장이_있으면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.HARD, "가 ".repeat(15) + "나", "둘째.", "셋째."),
            TITLE, ArticleDifficulty.HARD);
    }

    @Test
    void 문장이_3개가_아니면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "첫째.", "둘째."), TITLE, ArticleDifficulty.EASY);
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "첫째.", "둘째.", "셋째.", "넷째."),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE, null, KEYWORDS), TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 빈_문장이_있으면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "첫째.", "  ", "셋째."), TITLE, ArticleDifficulty.EASY);
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "첫째.", null, "셋째."), TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 제목이_입력값과_다르면_실패한다() {
        assertInvalid(output("한은 기준금리 동결", ArticleDifficulty.EASY, "첫째.", "둘째.", "셋째."),
            TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 난이도가_입력값과_다르면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.HARD, "첫째.", "둘째.", "셋째."), TITLE, ArticleDifficulty.EASY);
        assertInvalid(output(TITLE, null, "첫째.", "둘째.", "셋째."), TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 응답이_비어_있으면_실패한다() {
        assertInvalid(null, TITLE, ArticleDifficulty.EASY);
    }

    private void assertInvalid(ArticleSummaryOutput output, String title, ArticleDifficulty difficulty) {
        assertThatThrownBy(() -> validator.validate(output, title, difficulty))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AiErrorCode.AI_RESPONSE_INVALID);
    }

    private static ArticleSummaryOutput output(String title, ArticleDifficulty difficulty, String... sentences) {
        return new ArticleSummaryOutput(title, difficulty, ONE_LINE, Arrays.asList(sentences), KEYWORDS);
    }
}
