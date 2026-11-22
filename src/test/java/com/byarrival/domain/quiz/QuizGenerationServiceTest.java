package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.exception.AiResponseInvalidException;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.quiz.dto.QuizCheckOutput;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizChecker;
import com.byarrival.domain.quiz.service.QuizGenerationService;
import com.byarrival.domain.quiz.service.QuizGenerationService.QuizGenerationResult;
import com.byarrival.domain.quiz.service.QuizGenerator;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import com.byarrival.support.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 배치의 퀴즈 생성(AI-03, AI-04). AI 호출은 대신하고 검증·재요청·저장을 실제 DB로 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class QuizGenerationServiceTest {

    private static final TokenUsage USAGE = new TokenUsage(100, 50);
    private static final String EVIDENCE = "가나다라 첫째 문장입니다.";

    @Autowired
    private QuizGenerationService quizGenerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuizGenerator generator;

    @MockitoBean
    private QuizChecker checker;

    private Long feedId;
    private Long categoryId;
    private LocalDate serviceDate;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
        // 다른 테스트의 추천 후보와 섞이지 않는 서비스일
        // 검수는 기본으로 저장할 정답 하나만 맞다고 본다
        given(checker.check(any(), any())).willAnswer(invocation -> {
            ValidatedQuiz quiz = invocation.getArgument(1);
            // 테스트에서 다시 스텁할 때 인자 없이 한 번 불린다
            if (quiz == null) {
                return null;
            }
            return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq()), true, "정답만 맞아요."), USAGE);
        });
        serviceDate = LocalDate.of(2031, 1, 1).plusDays(Math.abs(UUID.randomUUID().getMostSignificantBits() % 3000));
    }

    @Test
    void 추천_후보_기사마다_퀴즈를_만들고_다시_돌리면_없는_기사만_만든다() {
        Long first = newArticle(true, "VISIBLE", "첫째 기사");
        Long retried = newArticle(true, "VISIBLE", "둘째 기사");
        Long failed = newArticle(true, "VISIBLE", "셋째 기사");
        Long notRecommendable = newArticle(false, "VISIBLE", "넷째 기사");
        Long hidden = newArticle(true, "HIDDEN", "다섯째 기사");
        given(generator.generate(source(first))).willReturn(result(valid("첫째 기사")));
        given(generator.generate(source(retried))).willReturn(result(invalid("둘째 기사")));
        given(generator.regenerate(source(retried), any(), anyString())).willReturn(result(valid("둘째 기사")));
        given(generator.generate(source(failed))).willReturn(result(invalid("셋째 기사")));
        given(generator.regenerate(source(failed), any(), anyString())).willReturn(result(invalid("셋째 기사")));

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failedArticleIds()).containsExactly(failed);
        // 실패한 기사는 최초 1회 + 다시 요청 3회
        verify(generator, times(3)).regenerate(source(failed), any(), anyString());
        verify(generator, never()).generate(source(notRecommendable));
        verify(generator, never()).generate(source(hidden));

        Map<String, Object> saved = jdbcTemplate.queryForMap(
            "SELECT question, choices::text AS choices, answer_seq, explanation, status FROM quiz WHERE article_id = ?",
            first);
        assertThat(saved.get("question")).isEqualTo("첫째 기사의 핵심은?");
        // 선택지는 정확히 3개이고 번호는 1·2·3이 하나씩이다. 저장된 정답 번호는 늘 정답 글("가나다라")을 가리킨다
        String choices = (String) saved.get("choices");
        List<Integer> seqs = JsonPath.read(choices, "$[*].seq");
        List<String> contents = JsonPath.read(choices, "$[*].content");
        assertThat(seqs).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(contents).containsExactlyInAnyOrder("가나다라", "마바사아", "자차카타");
        int answerSeq = (Integer) saved.get("answer_seq");
        List<String> answer = JsonPath.read(choices, "$[?(@.seq == " + answerSeq + ")].content");
        assertThat(answer).containsExactly("가나다라");
        assertThat(saved.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz WHERE article_id = ?", Integer.class,
            retried)).isEqualTo(1);

        clearInvocations(generator);
        QuizGenerationResult rerun = quizGenerationService.generate(serviceDate);

        assertThat(rerun.succeeded()).isZero();
        verify(generator, never()).generate(source(first));
        verify(generator).generate(source(failed));
    }

    @Test
    void 생성하는_사이_같은_기사의_퀴즈가_저장되면_실패가_아니라_이미_있음으로_센다() {
        Long article = newArticle(true, "VISIBLE", "겹친 기사");
        // AI 응답을 기다리는 사이 다른 배치 실행이 같은 기사의 퀴즈를 먼저 저장했다
        given(generator.generate(source(article))).willAnswer(invocation -> {
            jdbcTemplate.update("""
                INSERT INTO quiz (article_id, question, choices, answer_seq, explanation)
                VALUES (?, '먼저 저장된 문제?', '[{"seq":1,"content":"가"},{"seq":2,"content":"나"},{"seq":3,"content":"다"}]'::jsonb,
                    1, '먼저 저장됐어요.')
                """, article);
            return result(valid("겹친 기사"));
        });

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        assertThat(result.failedArticleIds()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT question FROM quiz WHERE article_id = ?", String.class, article))
            .isEqualTo("먼저 저장된 문제?");
    }

    @Test
    void 검수에서_정답이_하나로_정해지지_않으면_검수_의견을_알려_주고_다시_만든다() {
        Long article = newArticle(true, "VISIBLE", "검수 기사");
        QuizOutput ambiguous = valid("검수 기사");
        QuizOutput fixed = new QuizOutput(EVIDENCE, "검수 기사로 확인된 사실은?", "가나다라",
            List.of("마바사아", "자차카타"), "정답의 근거예요.");
        given(generator.generate(source(article))).willReturn(result(ambiguous));
        given(generator.regenerate(source(article), any(), anyString())).willReturn(result(fixed));
        // 첫 퀴즈는 오답 하나도 맞다고 보고, 다시 만든 퀴즈는 정답만 맞다고 본다
        given(checker.check(any(), argThat(quiz -> quiz != null && quiz.question().equals("검수 기사의 핵심은?"))))
            .willAnswer(invocation -> {
                ValidatedQuiz quiz = invocation.getArgument(1);
                int wrong = quiz.answerSeq() == 1 ? 2 : 1;
                return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq(), wrong), true, "오답도 맞아요."),
                    USAGE);
            });

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isEqualTo(1);
        // 생성 2회 + 검수 2회
        assertThat(result.calls()).isEqualTo(4);
        verify(generator).regenerate(source(article), any(),
            argThat(problems -> problems.contains("올바른 답으로 본 선택지가") && problems.contains("검수 의견: 오답도 맞아요.")));
        assertThat(jdbcTemplate.queryForObject("SELECT question FROM quiz WHERE article_id = ?", String.class, article))
            .isEqualTo("검수 기사로 확인된 사실은?");
    }

    @Test
    void 검수를_계속_통과하지_못하면_퀴즈를_저장하지_않는다() {
        Long article = newArticle(true, "VISIBLE", "틀린 기사");
        given(generator.generate(source(article))).willReturn(result(valid("틀린 기사")));
        given(generator.regenerate(source(article), any(), anyString())).willReturn(result(valid("틀린 기사")));
        given(checker.check(any(), any())).willReturn(
            new ClaudeResult<>(new QuizCheckOutput(List.of(), false, "맞는 답이 없어요."), USAGE));

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.failedArticleIds()).containsExactly(article);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz WHERE article_id = ?", Integer.class,
            article)).isZero();
    }

    @Test
    void 검수_호출이_실패하면_저장하지_않고_처음부터_다시_만든다() {
        Long article = newArticle(true, "VISIBLE", "끊긴 기사");
        given(generator.generate(source(article))).willReturn(result(valid("끊긴 기사")));
        given(checker.check(any(), any()))
            .willThrow(new BusinessException(AiErrorCode.AI_CALL_FAILED, "AnthropicIoException"))
            .willAnswer(invocation -> {
                ValidatedQuiz quiz = invocation.getArgument(1);
                return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq()), true, "정답만 맞아요."), USAGE);
            });

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isEqualTo(1);
        // 검수 실패 뒤에는 피드백 없이 처음부터 다시 만든다. 생성 2회 + 검수 2회(1회 실패)
        verify(generator, times(2)).generate(source(article));
        verify(generator, never()).regenerate(source(article), any(), anyString());
        assertThat(result.calls()).isEqualTo(4);
    }

    @Test
    void 검수에서_해설만_정답과_맞지_않아도_다시_만든다() {
        Long article = newArticle(true, "VISIBLE", "해설 기사");
        QuizOutput fixed = new QuizOutput(EVIDENCE, "해설 기사로 확인된 사실은?", "가나다라",
            List.of("마바사아", "자차카타"), "가나다라가 근거예요.");
        given(generator.generate(source(article))).willReturn(result(valid("해설 기사")));
        given(generator.regenerate(source(article), any(), anyString())).willReturn(result(fixed));
        given(checker.check(any(), argThat(quiz -> quiz != null && quiz.question().equals("해설 기사의 핵심은?"))))
            .willAnswer(invocation -> {
                ValidatedQuiz quiz = invocation.getArgument(1);
                return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq()), false, "해설이 오답을 설명해요."),
                    USAGE);
            });

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isEqualTo(1);
        verify(generator).regenerate(source(article), any(),
            argThat(problems -> problems.contains("해설이 정답 '가나다라'의 근거를 설명하지 않습니다")
                && !problems.contains("올바른 답으로 본 선택지가")));
        assertThat(jdbcTemplate.queryForObject("SELECT explanation FROM quiz WHERE article_id = ?", String.class,
            article)).isEqualTo("가나다라가 근거예요.");
    }

    private static QuizSource source(Long articleId) {
        return argThat(source -> source != null && source.articleId().equals(articleId));
    }

    private static ClaudeResult<QuizOutput> result(QuizOutput output) {
        return new ClaudeResult<>(output, USAGE);
    }

    private static QuizOutput valid(String title) {
        return new QuizOutput(EVIDENCE, title + "의 핵심은?", "가나다라", List.of("마바사아", "자차카타"),
            "정답의 근거예요.");
    }

    private static QuizOutput invalid(String title) {
        return new QuizOutput(EVIDENCE, "물음표 없는 문제", "가나다라", List.of("나"), "요약에 나와요.");
    }

    private Long newArticle(boolean recommendable, String visibility, String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '가나다라 첫째 문장입니다.', '둘째 문장입니다.', '셋째 문장입니다.', '한 줄 요약',
                'READY', ?, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            serviceDate.atTime(1, 0), serviceDate, recommendable, visibility);
    }

    @Test
    void 쓸_수_없는_검수_응답의_토큰도_비용에_넣는다() {
        Long article = newArticle(true, "VISIBLE", "잘린 기사");
        given(generator.generate(source(article))).willReturn(result(valid("잘린 기사")));
        given(checker.check(any(), any()))
            .willThrow(new AiResponseInvalidException(new TokenUsage(300, 4000), "stop_reason=max_tokens"))
            .willAnswer(invocation -> {
                ValidatedQuiz quiz = invocation.getArgument(1);
                return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq()), true, "정답만 맞아요."), USAGE);
            });

        QuizGenerationResult result = quizGenerationService.generate(serviceDate);

        assertThat(result.succeeded()).isEqualTo(1);
        // 생성 2회(각 100/50) + 잘린 검수(300/4000) + 성공한 검수(100/50)
        assertThat(result.calls()).isEqualTo(4);
        assertThat(result.inputTokens()).isEqualTo(600);
        assertThat(result.outputTokens()).isEqualTo(4150);
    }
}
