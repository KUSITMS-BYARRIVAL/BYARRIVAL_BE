package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizValidator;
import com.byarrival.domain.quiz.service.QuizValidator.CheckedQuiz;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuizValidatorTest {

    private static final String TITLE = "삼성 AI, 어디까지 왔나? 2026년 온디바이스 AI 반도체 현황";

    private final QuizValidator validator = new QuizValidator();

    @Test
    void 규칙을_지킨_퀴즈는_앞뒤_공백을_떼고_통과한다() {
        CheckedQuiz quiz = validator.validate(new QuizOutput(TITLE, " 엑시노스 2600은 Apple NPU의 몇 % 성능일까? ",
            " 85% ", List.of("65%", " 75% "), "Apple NPU 대비 85% 성능을 달성했어요."), TITLE);

        assertThat(quiz.question()).isEqualTo("엑시노스 2600은 Apple NPU의 몇 % 성능일까?");
        assertThat(quiz.answer()).isEqualTo("85%");
        assertThat(quiz.wrongAnswers()).containsExactly("65%", "75%");
        assertThat(quiz.explanation()).isEqualTo("Apple NPU 대비 85% 성능을 달성했어요.");
    }

    @Test
    void 어긴_규칙을_모두_모아_알려_준다() {
        QuizOutput output = new QuizOutput("다른 제목", "가".repeat(51), "", List.of("나".repeat(21)), "정답은 3번입니다.");

        assertThatThrownBy(() -> validator.validate(output, TITLE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("title을 입력받은 제목과 똑같이")
            .hasMessageContaining("question이(가) 51자")
            .hasMessageContaining("물음표")
            .hasMessageContaining("answer가 비어 있습니다")
            .hasMessageContaining("wrongAnswers는 정확히 2개")
            .hasMessageContaining("'~요.'로 끝내세요");
    }

    @Test
    void 오답이_정답이나_서로와_같거나_길면_실패한다() {
        QuizOutput sameAsAnswer = new QuizOutput(TITLE, "정답은?", "같은 답", List.of("같은 답", "나".repeat(21)), "근거예요.");
        QuizOutput duplicated = new QuizOutput(TITLE, "정답은?", "정답", List.of("오답", "오답"), "근거예요.");

        assertThatThrownBy(() -> validator.validate(sameAsAnswer, TITLE))
            .hasMessageContaining("1번 오답이 정답이나 다른 오답과 같습니다")
            .hasMessageContaining("2번 오답이(가) 21자");
        assertThatThrownBy(() -> validator.validate(duplicated, TITLE))
            .hasMessageContaining("2번 오답이 정답이나 다른 오답과 같습니다");
    }

    @Test
    void 정답_글이_제목에_그대로_있으면_실패한다() {
        String title = "구자욱, 해외 에이전트 선임…MLB 진출 도전";
        QuizOutput output = new QuizOutput(title, "구자욱이 FA를 앞두고 선임한 것은?", "해외 에이전트 선임",
            List.of("국내 구단과 재계약", "일본 리그 진출"), "MLB 진출을 위해 해외 에이전트를 선임했어요.");

        assertThatThrownBy(() -> validator.validate(output, title))
            .hasMessageContaining("제목에 그대로 있어");
    }

    @Test
    void 정답_숫자만_제목에_있으면_실패한다() {
        String title = "KBO FA 시장 개장! 원태인·구자욱·양의지 등 28명";
        QuizOutput output = new QuizOutput(title, "KBO가 공시한 FA 선수는 몇 명인가요?", "FA 자격 선수 28명",
            List.of("FA 자격 선수 30명", "FA 자격 선수 24명"), "KBO가 FA 28명을 공시했어요.");

        assertThatThrownBy(() -> validator.validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 제목의_숫자_뒤에_말줄임표가_붙어도_정답_숫자를_찾는다() {
        String title = "청약 경쟁률 28.5...분양 열기 지속";
        QuizOutput output = new QuizOutput(title, "이번 청약의 경쟁률은?", "28.5대 1", List.of("20대 1", "25대 1"),
            "청약 경쟁률은 28.5대 1이었어요.");

        assertThatThrownBy(() -> validator.validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 숫자_뒤_쉼표와_자릿수_쉼표를_구분한다() {
        // 제목의 "3,000,"은 끝 쉼표를 빼고 3000이며, 정답 "3000선"과 같은 숫자다
        String title = "코스피 3,000, 다시 돌파할까";
        QuizOutput output = new QuizOutput(title, "코스피가 돌파를 노리는 지수는?", "3000선", List.of("2500선", "2800선"),
            "코스피는 3000선 돌파를 노려요.");

        assertThatThrownBy(() -> validator.validate(output, title))
            .hasMessageContaining("정답의 숫자가 제목에 있어");
    }

    @Test
    void 제목에_없는_내용을_물으면_통과한다() {
        String title = "KBO FA 시장 개장! 원태인·구자욱·양의지 등 28명";
        QuizOutput output = new QuizOutput(title, "올해 FA 협상이 시작되는 날은?", "11월 18일",
            List.of("11월 20일", "12월 1일"), "FA 협상은 11월 18일부터 시작해요.");

        assertThat(validator.validate(output, title).answer()).isEqualTo("11월 18일");
    }

    @ParameterizedTest
    @ValueSource(strings = {"요약에 나온 사우디와의 역대 전적은?", "기사에 따르면 동결한 금리는?", "본문에서 말한 원인은?"})
    void 문제에_출처_표현을_쓰면_실패한다(String question) {
        QuizOutput output = new QuizOutput(TITLE, question, "정답", List.of("오답1", "오답2"), "근거예요.");

        assertThatThrownBy(() -> validator.validate(output, TITLE))
            .hasMessageContaining("question에")
            .hasMessageContaining("출처 표현");
    }

    @Test
    void 해설에_출처_표현을_쓰면_실패한다() {
        QuizOutput output = new QuizOutput(TITLE, "사우디와의 역대 전적은?", "5승 9무 5패", List.of("7승 6무 3패", "공개 안 됨"),
            "5승 9무 5패라고 요약에 나와요.");

        assertThatThrownBy(() -> validator.validate(output, TITLE))
            .hasMessageContaining("explanation에 '요약'");
    }
}
