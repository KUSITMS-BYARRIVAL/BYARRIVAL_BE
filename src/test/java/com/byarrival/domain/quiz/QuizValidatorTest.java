package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.domain.quiz.service.QuizValidator;
import com.byarrival.domain.quiz.service.QuizValidator.CheckedQuiz;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuizValidatorTest {

    private static final String TITLE = "삼성 AI, 어디까지 왔나? 2026년 온디바이스 AI 반도체 현황";

    // 근거 문장 규칙과 상관없는 테스트에서 쓰는 근거 문장. 입력 요약에 늘 들어 있다
    private static final String EVIDENCE = "가나다라마바사 근거 문장입니다.";

    private final QuizValidator validator = new QuizValidator();

    // 응답의 근거 문장이 요약 첫 문장으로 들어 있는 입력으로 검증한다
    private CheckedQuiz validate(QuizOutput output, String title) {
        return validator.validate(output, source(title, output.evidence()));
    }

    private static QuizSource source(String title, String evidence) {
        return new QuizSource(1L, title, "경제·금융", "한 줄 요약입니다.",
            List.of(evidence == null ? "" : evidence, "둘째 문장입니다.", "셋째 문장입니다."));
    }

    @Test
    void 규칙을_지킨_퀴즈는_앞뒤_공백을_떼고_통과한다() {
        CheckedQuiz quiz = validate(new QuizOutput("엑시노스 2600이 Apple NPU 대비 85% 성능을 냈습니다.", " 엑시노스 2600은 Apple NPU의 몇 % 성능일까? ",
            " 85% ", List.of("65%", " 75% "), "Apple NPU 대비 85% 성능을 달성했어요."), TITLE);

        assertThat(quiz.question()).isEqualTo("엑시노스 2600은 Apple NPU의 몇 % 성능일까?");
        assertThat(quiz.answer()).isEqualTo("85%");
        assertThat(quiz.wrongAnswers()).containsExactly("65%", "75%");
        assertThat(quiz.explanation()).isEqualTo("Apple NPU 대비 85% 성능을 달성했어요.");
    }

    @Test
    void 어긴_규칙을_모두_모아_알려_준다() {
        QuizOutput output = new QuizOutput(EVIDENCE, "가".repeat(51), "", List.of("나".repeat(21)), "정답은 3번입니다.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("question이(가) 51자")
            .hasMessageContaining("물음표")
            .hasMessageContaining("answer가 비어 있습니다")
            .hasMessageContaining("wrongAnswers는 정확히 2개")
            .hasMessageContaining("'~요.'로 끝내세요");
    }

    @Test
    void 오답이_정답이나_서로와_같거나_길면_실패한다() {
        QuizOutput sameAsAnswer = new QuizOutput(EVIDENCE, "정답은?", "같은 답", List.of("같은 답", "나".repeat(21)), "근거예요.");
        QuizOutput duplicated = new QuizOutput(EVIDENCE, "정답은?", "정답", List.of("오답", "오답"), "근거예요.");

        assertThatThrownBy(() -> validate(sameAsAnswer, TITLE))
            .hasMessageContaining("1번 오답이 정답이나 다른 오답과 같습니다")
            .hasMessageContaining("2번 오답이(가) 21자");
        assertThatThrownBy(() -> validate(duplicated, TITLE))
            .hasMessageContaining("2번 오답이 정답이나 다른 오답과 같습니다");
    }

    @Test
    void 정답_글이_제목에_그대로_있으면_실패한다() {
        String title = "구자욱, 해외 에이전트 선임…MLB 진출 도전";
        QuizOutput output = new QuizOutput(EVIDENCE, "구자욱이 FA를 앞두고 선임한 것은?", "해외 에이전트 선임",
            List.of("국내 구단과 재계약", "일본 리그 진출"), "MLB 진출을 위해 해외 에이전트를 선임했어요.");

        assertThatThrownBy(() -> validate(output, title))
            .hasMessageContaining("제목에 그대로 있어");
    }

    @Test
    void 정답_숫자만_제목에_있으면_실패한다() {
        String title = "KBO FA 시장 개장! 원태인·구자욱·양의지 등 28명";
        QuizOutput output = new QuizOutput(EVIDENCE, "KBO가 공시한 FA 선수는 몇 명인가요?", "FA 자격 선수 28명",
            List.of("FA 자격 선수 30명", "FA 자격 선수 24명"), "KBO가 FA 28명을 공시했어요.");

        assertThatThrownBy(() -> validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 제목의_숫자_뒤에_말줄임표가_붙어도_정답_숫자를_찾는다() {
        String title = "청약 경쟁률 28.5...분양 열기 지속";
        QuizOutput output = new QuizOutput(EVIDENCE, "이번 청약의 경쟁률은?", "28.5대 1", List.of("20대 1", "25대 1"),
            "청약 경쟁률은 28.5대 1이었어요.");

        assertThatThrownBy(() -> validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 숫자_뒤_쉼표와_자릿수_쉼표를_구분한다() {
        // 제목의 "3,000,"은 끝 쉼표를 빼고 3000이며, 정답 "3000선"과 같은 숫자다
        String title = "코스피 3,000, 다시 돌파할까";
        QuizOutput output = new QuizOutput(EVIDENCE, "코스피가 돌파를 노리는 지수는?", "3000선", List.of("2500선", "2800선"),
            "코스피는 3000선 돌파를 노려요.");

        assertThatThrownBy(() -> validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 제목에_없는_내용을_물으면_통과한다() {
        String title = "KBO FA 시장 개장! 원태인·구자욱·양의지 등 28명";
        QuizOutput output = new QuizOutput("FA 협상은 11월 18일부터 시작됩니다.", "올해 FA 협상이 시작되는 날은?", "11월 18일",
            List.of("11월 20일", "12월 1일"), "FA 협상은 11월 18일부터 시작해요.");

        assertThat(validate(output, title).answer()).isEqualTo("11월 18일");
    }

    @ParameterizedTest
    @ValueSource(strings = {"요약에 나온 사우디와의 역대 전적은?", "기사에 따르면 동결한 금리는?", "본문에서 말한 원인은?"})
    void 문제에_출처_표현을_쓰면_실패한다(String question) {
        QuizOutput output = new QuizOutput(EVIDENCE, question, "정답", List.of("오답1", "오답2"), "근거예요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("question에")
            .hasMessageContaining("출처 표현");
    }

    @Test
    void 해설에_출처_표현을_쓰면_실패한다() {
        QuizOutput output = new QuizOutput(EVIDENCE, "사우디와의 역대 전적은?", "5승 9무 5패", List.of("7승 6무 3패", "공개 안 됨"),
            "5승 9무 5패라고 요약에 나와요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("explanation에 '요약'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"정부가 추진하는 보안 강화 방안이 아닌 것은?", "선전에서 나타난 변화가 아닌것은?",
        "이번 발표에 포함되지 않은 것은?", "설명으로 틀린 것은?", "금리 동결과 관련 없는 내용은?"})
    void 부정형_문제는_실패한다(String question) {
        QuizOutput output = new QuizOutput(EVIDENCE, question, "정답", List.of("오답1", "오답2"), "근거예요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("부정형 문제");
    }

    @Test
    void 근거_문장이_입력_요약에_없으면_실패한다() {
        QuizOutput output = new QuizOutput("요약에 없는 문장을 지어냈습니다.", "엑시노스 성능은?", "85%",
            List.of("65%", "75%"), "85% 성능을 냈어요.");

        assertThatThrownBy(() -> validator.validate(output, source(TITLE, "엑시노스가 85% 성능을 냈습니다.")))
            .hasMessageContaining("evidence에는 입력의");
    }

    @Test
    void 정답이_근거_문장에_없으면_실패한다() {
        // 추성훈 기사처럼 근거 문장과 다른 사실을 정답으로 쓴 경우
        QuizOutput output = new QuizOutput("뼈가 부러진 상태에서도 촬영을 이어갔습니다.", "촬영을 이어간 이유는?",
            "제작진과의 약속", List.of("휴식 부족", "출연료 문제"), "약속 때문이에요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("evidence 문장에 없습니다");
    }

    @Test
    void 정답_숫자가_근거_문장과_다르면_실패한다() {
        QuizOutput output = new QuizOutput("국내 30만 명 분석에서 신고율 0.035%였습니다.", "동시접종 이상반응 신고율은?",
            "신고율 0.35%", List.of("신고율 3.5%", "신고율 0.035%"), "신고율은 0.35%였어요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("evidence 문장에 없습니다");
    }

    @Test
    void 근거_문장의_일부만_옮기거나_조사가_달라도_통과한다() {
        String evidence = "양팀은 강점을 주고받으며 약점 보완을 노립니다.";
        QuizOutput output = new QuizOutput("강점을 주고받으며 약점 보완을 노립니다", "코칭스태프 교류로 노리는 것은?",
            "강점 주고받기와 약점 보완", List.of("구단 간 합병", "선수 트레이드"), "강점을 주고받으며 약점을 보완하려 해요.");

        assertThat(validator.validate(output, source(TITLE, evidence)).answer()).isEqualTo("강점 주고받기와 약점 보완");
    }

    @Test
    void 근거_문장은_한_줄_요약에서_옮겨도_된다() {
        QuizSource source = new QuizSource(1L, TITLE, "경제·금융", "외국인이 1조6998억원을 순매도했습니다.",
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."));
        QuizOutput output = new QuizOutput("외국인이 1조6998억원을 순매도했습니다.", "외국인이 순매도한 금액은?",
            "1조6998억원", List.of("8502억원", "2조3000억원"), "외국인이 1조6998억원을 순매도했어요.");

        assertThat(validator.validate(output, source).answer()).isEqualTo("1조6998억원");
    }

    @ParameterizedTest
    @ValueSource(strings = {"한국은행이 기준금리를 올리지 않은 이유는?", "투자 한파에서 살아남은 스타트업의 공통점은?"})
    void 부정어가_있어도_부정형_선택_문제가_아니면_통과한다(String question) {
        QuizOutput output = new QuizOutput("물가 안정을 위해 금리를 유지했습니다.", question, "물가 안정", List.of("환율 급등", "수출 감소"), "물가가 안정됐어요.");

        assertThat(validate(output, TITLE).question()).isEqualTo(question);
    }

    @Test
    void 소수점을_지워_숫자를_바꾼_근거_문장은_입력_문장이_아니다() {
        QuizOutput output = new QuizOutput("정부는 금리를 35%로 유지했습니다.", "정부가 유지한 금리는?", "35%",
            List.of("30%", "40%"), "금리를 35%로 유지했어요.");

        assertThatThrownBy(() -> validator.validate(output, source(TITLE, "정부는 금리를 3.5%로 유지했습니다.")))
            .hasMessageContaining("evidence에는 입력의");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.5%", "3.50%"})
    void 소수점_끝_0이_달라도_같은_숫자면_통과한다(String answer) {
        String evidence = answer.equals("3.5%") ? "기준금리는 3.50%로 유지됐습니다." : "기준금리는 3.5%로 유지됐습니다.";
        QuizOutput output = new QuizOutput(evidence, "유지된 기준금리는?", answer, List.of("3.25%", "3.75%"),
            "기준금리가 유지됐어요.");

        assertThat(validate(output, TITLE).answer()).isEqualTo(answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"다음 중 사실과 다른 것은?", "옳지 않은 답은?", "설명으로 잘못된 것은?"})
    void 다른_표현의_부정형_문제도_실패한다(String question) {
        QuizOutput output = new QuizOutput(EVIDENCE, question, "정답", List.of("오답1", "오답2"), "근거예요.");

        assertThatThrownBy(() -> validate(output, TITLE))
            .hasMessageContaining("부정형 문제");
    }

    @Test
    void 음수_부호를_떼고_옮긴_근거_문장은_입력_문장이_아니다() {
        QuizOutput output = new QuizOutput("0.5%로 유지됐습니다.", "유지된 기준금리는?", "0.5%",
            List.of("1.0%", "1.5%"), "기준금리는 0.5%로 유지됐어요.");

        assertThatThrownBy(() -> validator.validate(output, source(TITLE, "기준금리는 -0.5%로 유지됐습니다.")))
            .hasMessageContaining("evidence에는 입력의");
    }
}
