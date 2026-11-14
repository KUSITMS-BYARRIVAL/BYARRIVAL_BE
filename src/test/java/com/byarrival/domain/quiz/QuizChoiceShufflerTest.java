package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.quiz.service.QuizChoiceShuffler;
import com.byarrival.domain.quiz.service.QuizValidator.CheckedQuiz;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class QuizChoiceShufflerTest {

    private static final CheckedQuiz QUIZ = new CheckedQuiz("한국은행이 동결한 기준금리는?", "연 3.0%",
        List.of("연 2.5%", "연 3.5%"), "기준금리를 연 3.0%로 동결했어요.");

    @Test
    void 정답_번호는_늘_정답_글을_가리킨다() {
        QuizChoiceShuffler shuffler = new QuizChoiceShuffler(new Random(1));

        for (int i = 0; i < 50; i++) {
            ValidatedQuiz quiz = shuffler.shuffle(QUIZ);

            assertThat(quiz.choices()).containsExactlyInAnyOrder("연 3.0%", "연 2.5%", "연 3.5%");
            assertThat(quiz.choices().get(quiz.answerSeq() - 1)).isEqualTo("연 3.0%");
            assertThat(quiz.question()).isEqualTo(QUIZ.question());
            assertThat(quiz.explanation()).isEqualTo(QUIZ.explanation());
        }
    }

    @Test
    void 정답_위치가_한_자리에_몰리지_않는다() {
        QuizChoiceShuffler shuffler = new QuizChoiceShuffler(new Random(42));
        Map<Integer, Integer> positions = new HashMap<>();

        for (int i = 0; i < 300; i++) {
            positions.merge(shuffler.shuffle(QUIZ).answerSeq(), 1, Integer::sum);
        }

        // 300번 섞으면 1·2·3번이 각각 대략 100번씩 나온다
        assertThat(positions).containsOnlyKeys(1, 2, 3);
        assertThat(positions.values()).allMatch(count -> count > 60);
    }
}
