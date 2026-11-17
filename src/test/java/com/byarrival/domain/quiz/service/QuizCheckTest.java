package com.byarrival.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.domain.quiz.config.QuizCheckProperties;
import com.byarrival.domain.quiz.dto.QuizCheckOutput;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 퀴즈 검수(AI-04)의 판정 규칙, 검수 입력, 설정값 검증
 */
class QuizCheckTest {

    // 정답은 2번 "나"
    private static final ValidatedQuiz QUIZ = new ValidatedQuiz("핵심은?", List.of("가", "나", "다"), 2, "나가 근거예요.");

    @Test
    void 정답_하나만_맞고_해설이_맞으면_통과한다() {
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(List.of(2), true, "맞아요."), QUIZ)).isNull();
    }

    @Test
    void 같은_번호가_겹쳐_와도_하나로_본다() {
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(List.of(2, 2), true, ""), QUIZ)).isNull();
    }

    @Test
    void 다른_선택지도_맞다고_보면_그_선택지_글을_알려_준다() {
        String problems = QuizGenerationService.checkProblems(new QuizCheckOutput(List.of(2, 3), true, "다도 맞아요."), QUIZ);

        assertThat(problems)
            .contains("올바른 답으로 본 선택지가 '나', '다'였습니다")
            .contains("검수 의견: 다도 맞아요.");
    }

    @Test
    void 맞는_선택지가_없거나_목록이_비어_있으면_실패한다() {
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(List.of(), true, null), QUIZ))
            .contains("올바른 답으로 본 선택지가 없었습니다");
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(null, true, null), QUIZ))
            .contains("올바른 답으로 본 선택지가 없었습니다");
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(Arrays.asList(null, 9), true, null), QUIZ))
            .contains("올바른 답으로 본 선택지가 없었습니다");
    }

    @Test
    void 해설이_정답과_맞지_않으면_실패한다() {
        assertThat(QuizGenerationService.checkProblems(new QuizCheckOutput(List.of(2), false, ""), QUIZ))
            .contains("해설이 정답 '나'의 근거를 설명하지 않습니다")
            .doesNotContain("올바른 답으로 본 선택지가")
            .doesNotContain("검수 의견");
    }

    @Test
    void 검수_응답이_비어_있으면_실패한다() {
        assertThat(QuizGenerationService.checkProblems(null, QUIZ))
            .contains("올바른 답으로 본 선택지가 없었습니다")
            .contains("해설이 정답 '나'의 근거를 설명하지 않습니다");
    }

    @Test
    void 검수에는_요약과_번호를_붙인_선택지와_해설을_보낸다() {
        QuizSource source = new QuizSource(1L, "제목", "경제·금융", "한 줄 요약입니다.", List.of("첫째.", "둘째.", "셋째."));

        String message = QuizChecker.userMessage(source, QUIZ);

        assertThat(message)
            .contains("<title>제목</title>")
            .contains("<one_line>한 줄 요약입니다.</one_line>")
            .contains("<summary>\n첫째.\n둘째.\n셋째.\n</summary>")
            .contains("<question>핵심은?</question>")
            .contains("<choices>\n1. 가\n2. 나\n3. 다\n</choices>")
            .contains("<explanation>나가 근거예요.</explanation>")
            // 검수자가 정답을 미리 알면 안 된다
            .doesNotContain("answer");
    }

    @Test
    void 검수_설정값이_비었거나_잘못되면_시작하지_않는다() {
        assertThatThrownBy(() -> new QuizCheckProperties(" ", 4000, "medium")).hasMessageContaining("model");
        assertThatThrownBy(() -> new QuizCheckProperties("claude-haiku-5-5", 0, "medium"))
            .hasMessageContaining("max-tokens");
        assertThatThrownBy(() -> new QuizCheckProperties("claude-haiku-5-5", 4000, null)).hasMessageContaining("effort");
    }
}
