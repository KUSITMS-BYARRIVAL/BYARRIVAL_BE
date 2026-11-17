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
    // 근거 구절 규칙과 상관없는 테스트에서 쓰는 근거 구절과, 그 구절이 모두 든 본문
    private static final List<String> EVIDENCE = List.of("한국은행이 기준금리를 동결했다", "물가 상승세가 둔화됐다는 판단이다",
        "시장에서는 연내 인하를 점친다");
    private static final String CONTENT = "한국은행이 기준금리를 동결했다. 물가 상승세가 둔화됐다는 판단이다. "
        + "시장에서는 연내 인하를 점친다. 기준금리는 연 3.0%다.";

    private final ArticleSummaryValidator validator = new ArticleSummaryValidator();

    @Test
    void 제목과_난이도가_같고_30자_이내_3문장이면_통과한다() {
        ValidatedSummary summary = validator.validate(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY,
                " " + "나".repeat(37) + "니다. ", List.of("가".repeat(27) + "니다.", " 둘째 문장입니다. ", "셋째 문장입니다"), EVIDENCE,
                List.of(" 기준금리 ", "나".repeat(10), "물가 상승")),
            TITLE, CONTENT, ArticleDifficulty.EASY);

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
        return new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE, List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), EVIDENCE,
            keywords);
    }

    @Test
    void 문장이_니다로_끝나지_않으면_실패한다() {
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "금리를 동결했다.", "둘째 문장입니다.", "셋째 문장입니다."),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(output(TITLE, ArticleDifficulty.EASY, "시총 258조원 증발", "둘째 문장입니다.", "셋째 문장입니다."),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "한은 기준금리 동결",
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), EVIDENCE, KEYWORDS), TITLE, ArticleDifficulty.EASY);
    }

    @Test
    void 제목을_그대로_반복하면_실패한다() {
        String title = "환율이 12원 내렸습니다";
        assertInvalid(new ArticleSummaryOutput(title, ArticleDifficulty.EASY, title,
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), EVIDENCE, KEYWORDS), title, ArticleDifficulty.EASY);
    }

    @Test
    void 어긴_규칙을_모두_모아_다시_요청할_때_쓸_수_있게_알려준다() {
        String longSentence = "가".repeat(36) + "니다.";

        assertThatThrownBy(() -> validator.validate(
            output(TITLE, ArticleDifficulty.EASY, "첫째 문장입니다.", longSentence, "셋째 문장이다."),
            TITLE, CONTENT, ArticleDifficulty.EASY))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("summary 2번 문장이 39자입니다")
            .hasMessageContaining(longSentence)
            .hasMessageContaining("summary 3번 문장을");
    }

    @Test
    void 한_줄_요약이_40자를_넘거나_비어_있으면_실패한다() {
        List<String> sentences = List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다.");
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "나".repeat(39) + "니다", sentences, EVIDENCE, KEYWORDS),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, " ", sentences, EVIDENCE, KEYWORDS),
            TITLE, ArticleDifficulty.EASY);
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, null, sentences, EVIDENCE, KEYWORDS),
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
        assertInvalid(new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE, null, EVIDENCE, KEYWORDS), TITLE, ArticleDifficulty.EASY);
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
        assertThatThrownBy(() -> validator.validate(output, title, CONTENT, difficulty))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AiErrorCode.AI_RESPONSE_INVALID);
    }

    private static ArticleSummaryOutput output(String title, ArticleDifficulty difficulty, String... sentences) {
        return new ArticleSummaryOutput(title, difficulty, ONE_LINE, Arrays.asList(sentences), EVIDENCE, KEYWORDS);
    }

    @Test
    void 근거_구절이_본문에_없으면_실패한다() {
        ArticleSummaryOutput output = new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE,
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."),
            List.of("한국은행이 기준금리를 동결했다", "본문에 없는 지어낸 구절이다", "시장에서는 연내 인하를 점친다"), KEYWORDS);

        assertThatThrownBy(() -> validator.validate(output, TITLE, CONTENT, ArticleDifficulty.EASY))
            .hasMessageContaining("evidence 2번이 기사 본문에 없습니다");
    }

    @Test
    void 근거_구절이_3개가_아니면_실패한다() {
        ArticleSummaryOutput output = new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE,
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), List.of("한국은행이 기준금리를 동결했다"), KEYWORDS);

        assertThatThrownBy(() -> validator.validate(output, TITLE, CONTENT, ArticleDifficulty.EASY))
            .hasMessageContaining("evidence는 summary 문장마다 하나씩");
    }

    @Test
    void 요약_문장의_숫자가_근거_구절에_없으면_실패한다() {
        ArticleSummaryOutput output = new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, ONE_LINE,
            List.of("기준금리를 연 3.5%로 동결했습니다.", "둘째 문장입니다.", "셋째 문장입니다."),
            List.of("기준금리는 연 3.0%다", "물가 상승세가 둔화됐다는 판단이다", "시장에서는 연내 인하를 점친다"), KEYWORDS);

        assertThatThrownBy(() -> validator.validate(output, TITLE, CONTENT, ArticleDifficulty.EASY))
            .hasMessageContaining("summary 1번 문장의 숫자 [3.5]가 evidence 1번 구절에 없습니다");
    }

    @Test
    void 한_줄_요약의_숫자가_본문과_제목에_없으면_실패한다() {
        ArticleSummaryOutput output = new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "기준금리를 5회 연속 동결했습니다.",
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), EVIDENCE, KEYWORDS);

        assertThatThrownBy(() -> validator.validate(output, TITLE, CONTENT, ArticleDifficulty.EASY))
            .hasMessageContaining("oneLine의 숫자 [5]가 기사 본문에 없습니다");
    }

    @Test
    void 숫자_표기와_공백_문장부호가_달라도_본문과_같으면_통과한다() {
        // 본문의 "3.0%"와 요약의 "3%"는 같은 값이고, 근거 구절의 공백·마침표 차이는 무시한다
        ArticleSummaryOutput output = new ArticleSummaryOutput(TITLE, ArticleDifficulty.EASY, "기준금리를 연 3%로 묶었습니다.",
            List.of("기준금리를 연 3%로 동결했습니다.", "물가 둔화를 반영했습니다.", "연내 인하 전망이 나옵니다."),
            List.of("기준금리는  연 3.0%다.", "물가 상승세가 둔화됐다는 판단이다", "시장에서는 연내 인하를 점친다."), KEYWORDS);

        assertThat(validator.validate(output, TITLE, CONTENT, ArticleDifficulty.EASY).sentences())
            .containsExactly("기준금리를 연 3%로 동결했습니다.", "물가 둔화를 반영했습니다.", "연내 인하 전망이 나옵니다.");
    }
}
