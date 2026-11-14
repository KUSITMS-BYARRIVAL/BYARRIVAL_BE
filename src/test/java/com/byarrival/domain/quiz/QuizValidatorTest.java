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

    private static final String TITLE = "엑시노스 2600, 애플 NPU 85% 성능";

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
