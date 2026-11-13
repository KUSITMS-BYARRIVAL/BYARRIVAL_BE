package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizValidator;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizValidatorTest {

    private static final String TITLE = "엑시노스 2600, 애플 NPU 85% 성능";

    private final QuizValidator validator = new QuizValidator();

    @Test
    void 규칙을_지킨_퀴즈는_앞뒤_공백을_떼고_통과한다() {
        ValidatedQuiz quiz = validator.validate(new QuizOutput(TITLE, " 엑시노스 2600은 Apple NPU의 몇 % 성능일까? ",
            List.of("65%", " 75% ", "85%"), 3, "Apple NPU 대비 85% 성능을 달성했어요."), TITLE);

        assertThat(quiz.question()).isEqualTo("엑시노스 2600은 Apple NPU의 몇 % 성능일까?");
        assertThat(quiz.choices()).containsExactly("65%", "75%", "85%");
        assertThat(quiz.answerSeq()).isEqualTo(3);
        assertThat(quiz.explanation()).isEqualTo("Apple NPU 대비 85% 성능을 달성했어요.");
    }

    @Test
    void 어긴_규칙을_모두_모아_알려_준다() {
        QuizOutput output = new QuizOutput("다른 제목", "가".repeat(51), List.of("같은 답", "같은 답", "나".repeat(21), "넷"),
            4, "정답은 3번입니다.");

        assertThatThrownBy(() -> validator.validate(output, TITLE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("title을 입력받은 제목과 똑같이")
            .hasMessageContaining("question이 51자")
            .hasMessageContaining("물음표")
            .hasMessageContaining("choices는 정확히 3개")
            .hasMessageContaining("answer는 1부터 3")
            .hasMessageContaining("'~요.'로 끝내세요");
    }

    @Test
    void 선택지가_겹치거나_길면_실패한다() {
        QuizOutput output = new QuizOutput(TITLE, "정답은?", List.of("같은 답", "같은 답", "나".repeat(21)), 1,
            "근거예요.");

        assertThatThrownBy(() -> validator.validate(output, TITLE))
            .hasMessageContaining("2번 선택지가 다른 선택지와 같습니다")
            .hasMessageContaining("3번 선택지가 21자");
    }
}
