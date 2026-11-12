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

import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizGenerationService;
import com.byarrival.domain.quiz.service.QuizGenerationService.QuizGenerationResult;
import com.byarrival.domain.quiz.service.QuizGenerator;
import com.byarrival.domain.quiz.service.QuizGenerator.QuizSource;
import com.byarrival.support.TestcontainersConfig;
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

    @Autowired
    private QuizGenerationService quizGenerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuizGenerator generator;

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
        assertThat((String) saved.get("choices")).contains("\"seq\": 1", "\"content\": \"가\"", "\"seq\": 3");
        assertThat(saved.get("answer_seq")).isEqualTo(2);
        assertThat(saved.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quiz WHERE article_id = ?", Integer.class,
            retried)).isEqualTo(1);

        clearInvocations(generator);
        QuizGenerationResult rerun = quizGenerationService.generate(serviceDate);

        assertThat(rerun.succeeded()).isZero();
        verify(generator, never()).generate(source(first));
        verify(generator).generate(source(failed));
    }

    private static QuizSource source(Long articleId) {
        return argThat(source -> source != null && source.articleId().equals(articleId));
    }

    private static ClaudeResult<QuizOutput> result(QuizOutput output) {
        return new ClaudeResult<>(output, USAGE);
    }

    private static QuizOutput valid(String title) {
        return new QuizOutput(title, title + "의 핵심은?", List.of("가", "나", "다"), 2, "요약에 나와요.");
    }

    private static QuizOutput invalid(String title) {
        return new QuizOutput(title, "물음표 없는 문제", List.of("가", "나"), 5, "해설");
    }

    private Long newArticle(boolean recommendable, String visibility, String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', ?, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            serviceDate.atTime(1, 0), serviceDate, recommendable, visibility);
    }
}
