package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.service.ArticleCrawler;
import com.byarrival.domain.article.service.ArticleCrawler.CrawlResult;
import com.byarrival.domain.article.service.ArticleCrawler.CrawledArticle;
import com.byarrival.domain.article.service.ArticleSummaryGenerator;
import com.byarrival.domain.quiz.dto.QuizCheckOutput;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.service.QuizChecker;
import com.byarrival.domain.quiz.service.QuizGenerator;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 기사 재생성(ARTICLE-06). AI 호출과 원문 가져오기는 대신하고, 검증·저장·상태 확인은 실제 DB로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminArticleRegenerateFlowIntegrationTest {

    private static final String ARTICLES = "/api/admin/articles";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private static final TokenUsage USAGE = new TokenUsage(100, 50, 10, 200);
    private static final String BODY = "기사 본문입니다. ".repeat(50);
    private static final List<String> EVIDENCE = List.of("기사 본문입니다. 기사 본문입니다.", "기사 본문입니다. 기사 본문입니다.",
        "기사 본문입니다. 기사 본문입니다.");
    private static final String NEW_FIRST_SENTENCE = "가나다라 새 첫째 문장입니다.";
    private static final String OLD_FIRST_SENTENCE = "가나다라 첫째 문장입니다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleContentRepository articleContentRepository;

    @MockitoBean
    private ArticleSummaryGenerator summaryGenerator;

    @MockitoBean
    private ArticleCrawler crawler;

    @MockitoBean
    private QuizGenerator quizGenerator;

    @MockitoBean
    private QuizChecker quizChecker;

    private Cookie cookie;
    private Long feedId;
    private Long categoryId;
    private LocalDate serviceDate;

    @BeforeEach
    void setUp() throws Exception {
        String email = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", email, PASSWORD_HASH);
        cookie = login(email);
        feedId = jdbcTemplate.queryForObject("INSERT INTO rss_feed (press_name, feed_url) VALUES (?, ?) RETURNING id",
            Long.class, "테스트일보", "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
        // 다른 테스트의 기사·배치 기록과 섞이지 않는 서비스일
        serviceDate = LocalDate.of(2035, 1, 1).plusDays(Math.abs(UUID.randomUUID().getMostSignificantBits() % 3000));
        given(crawler.crawl(anyString())).willReturn(
            CrawlResult.success(new CrawledArticle("https://press.example.com/" + UUID.randomUUID(), BODY)));
        given(summaryGenerator.generate(anyString(), anyString(), any())).willAnswer(invocation ->
            new ClaudeResult<>(summary(invocation.getArgument(0), invocation.getArgument(2)), USAGE));
        given(quizGenerator.generate(any())).willReturn(new ClaudeResult<>(quiz("새 퀴즈의 핵심은?"), USAGE));
        given(quizChecker.check(any(), any())).willAnswer(invocation -> {
            ValidatedQuiz quiz = invocation.getArgument(1);
            if (quiz == null) {
                return null;
            }
            return new ClaudeResult<>(new QuizCheckOutput(List.of(quiz.answerSeq()), true, "정답만 맞아요."), USAGE);
        });
    }

    @Test
    void 숨긴_기사의_요약을_다시_만들면_원문을_다시_가져와_덮어쓰고_퀴즈도_새_버전으로_만든다() throws Exception {
        Long article = readyArticle("HIDDEN", "HARD");
        Long oldQuiz = activeQuiz(article, "옛 퀴즈의 핵심은?");

        regenerate(article, "summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("SUCCESS"))
            .andExpect(jsonPath("$.data.quiz").value("SUCCESS"))
            .andExpect(jsonPath("$.data.failureReason").doesNotExist())
            .andExpect(jsonPath("$.data.article.state").value("HIDDEN"))
            .andExpect(jsonPath("$.data.article.summary[0]").value(NEW_FIRST_SENTENCE))
            .andExpect(jsonPath("$.data.article.difficulty").value("HARD"))
            .andExpect(jsonPath("$.data.article.quiz.question").value("새 퀴즈의 핵심은?"));

        // 난이도는 바꾸지 않고 요약을 만든다
        verify(summaryGenerator).generate(anyString(), eq(BODY), eq(ArticleDifficulty.HARD));
        List<Map<String, Object>> quizzes = quizzesOf(article);
        assertThat(quizzes).hasSize(2);
        assertThat(quizzes.get(0).get("id")).isEqualTo(oldQuiz);
        assertThat(quizzes.get(0).get("status")).isEqualTo("INACTIVE");
        assertThat(quizzes.get(1).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void 요약이_없는_오류_기사는_남은_본문으로_요약하고_성공하면_바로_노출된다() throws Exception {
        Long article = cleanedArticleWithContent();

        regenerate(article, "summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("SUCCESS"))
            .andExpect(jsonPath("$.data.quiz").value("SUCCESS"))
            .andExpect(jsonPath("$.data.article.state").value("EXPOSED"));

        verify(crawler, never()).crawl(anyString());
        // 난이도가 없던 기사는 그 카테고리에서 적은 쪽(같으면 EASY)을 준다
        verify(summaryGenerator).generate(anyString(), eq(BODY), eq(ArticleDifficulty.EASY));
        assertThat(articleContentRepository.existsById(article)).isFalse();
    }

    @Test
    void 퀴즈만_다시_만들면_요약은_그대로_두고_이전_버전을_모두_남긴다() throws Exception {
        Long article = readyArticle("HIDDEN", "EASY");
        activeQuiz(article, "첫 버전의 핵심은?");
        Long routeQuiz = assignedRouteQuiz(article);
        // 요약은 그대로이므로 퀴즈 근거도 지금 요약의 문장이다
        given(quizGenerator.generate(any())).willReturn(
            new ClaudeResult<>(quiz("두 번째 버전의 핵심은?", OLD_FIRST_SENTENCE), USAGE));

        regenerate(article, "quiz")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("SKIPPED"))
            .andExpect(jsonPath("$.data.quiz").value("SUCCESS"))
            .andExpect(jsonPath("$.data.article.summary[0]").value(OLD_FIRST_SENTENCE));
        given(quizGenerator.generate(any())).willReturn(
            new ClaudeResult<>(quiz("세 번째 버전의 핵심은?", OLD_FIRST_SENTENCE), USAGE));
        regenerate(article, "quiz")
            .andExpect(jsonPath("$.data.article.quiz.question").value("세 번째 버전의 핵심은?"));

        List<Map<String, Object>> quizzes = quizzesOf(article);
        assertThat(quizzes).extracting(row -> row.get("status")).containsExactly("INACTIVE", "INACTIVE", "ACTIVE");
        // 이미 퀴즈를 받은 노선은 받은 버전을 그대로 쓴다
        assertThat(jdbcTemplate.queryForObject("SELECT quiz_id FROM route_quiz WHERE id = ?", Long.class, routeQuiz))
            .isEqualTo(quizzes.get(0).get("id"));
        verify(summaryGenerator, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void 원문을_다시_가져오지_못하면_요약을_바꾸지_않고_실패를_알려_준다() throws Exception {
        Long article = readyArticle("HIDDEN", "EASY");
        given(crawler.crawl(anyString())).willReturn(CrawlResult.fetchFailure());

        regenerate(article, "summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("FAILED"))
            .andExpect(jsonPath("$.data.quiz").value("SKIPPED"))
            .andExpect(jsonPath("$.data.failureReason").isNotEmpty())
            .andExpect(jsonPath("$.data.article.summary[0]").value(OLD_FIRST_SENTENCE));
    }

    @Test
    void 요약은_성공하고_퀴즈가_실패하면_요약만_저장한다() throws Exception {
        Long article = readyArticle("HIDDEN", "EASY");
        QuizOutput invalid = new QuizOutput("가나다라 새 첫째 문장입니다.", "물음표 없는 문제", "가나다라", List.of("나"), "요약");
        given(quizGenerator.generate(any())).willReturn(new ClaudeResult<>(invalid, USAGE));
        given(quizGenerator.regenerate(any(), any(), anyString())).willReturn(new ClaudeResult<>(invalid, USAGE));

        regenerate(article, "summary")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("SUCCESS"))
            .andExpect(jsonPath("$.data.quiz").value("FAILED"))
            .andExpect(jsonPath("$.data.failureReason").isNotEmpty())
            .andExpect(jsonPath("$.data.article.summary[0]").value(NEW_FIRST_SENTENCE));
    }

    @Test
    void 노출_중이거나_원문이_삭제됐거나_없는_기사는_재생성하지_않는다() throws Exception {
        Long exposed = readyArticle("VISIBLE", "EASY");
        activeQuiz(exposed, "노출 중 퀴즈의 핵심은?");
        Long deleted = readyArticle("DELETED", "EASY");

        regenerate(exposed, "summary")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_ARTICLE_NOT_REGENERATABLE"));
        regenerate(exposed, "quiz").andExpect(status().isConflict());
        regenerate(deleted, "summary")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_ARTICLE_UNAVAILABLE"));
        regenerate(999_999_999L, "summary").andExpect(status().isNotFound());
        verify(summaryGenerator, never()).generate(anyString(), anyString(), any());
    }

    @Test
    void 요약이_없는_기사는_퀴즈만_다시_만들_수_없다() throws Exception {
        Long article = cleanedArticleWithContent();

        regenerate(article, "quiz")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_ARTICLE_NO_SUMMARY"));
    }

    private ResultActions regenerate(Long articleId, String step) throws Exception {
        return mockMvc.perform(post(ARTICLES + "/" + articleId + "/regenerate/" + step).cookie(cookie));
    }

    private Cookie login(String email) throws Exception {
        String setCookie = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        return new Cookie(AdminCookieFactory.COOKIE_NAME, pair.substring(pair.indexOf('=') + 1));
    }

    // 요약까지 마친 기사. 본문은 요약 뒤 지워졌다
    private Long readyArticle(String visibility, String difficulty) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, source_url, title, press_name, published_at,
                service_date, difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '재생성 기사', '테스트일보', ?, ?, ?, 2.0, '가나다라 첫째 문장입니다.', '둘째 문장입니다.',
                '셋째 문장입니다.', '한 줄 요약', 'READY', TRUE, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            "https://press.example.com/" + UUID.randomUUID(), serviceDate.atTime(1, 0), serviceDate, difficulty,
            visibility);
    }

    // 정제까지 마쳤지만 요약에 실패한 기사. 본문이 남아 있고 난이도가 없다
    private Long cleanedArticleWithContent() {
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, source_url, title, press_name, published_at,
                service_date, reading_minutes, process_status)
            VALUES (?, ?, ?, ?, '요약 실패 기사', '테스트일보', ?, ?, 2.0, 'CLEANED')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            "https://press.example.com/" + UUID.randomUUID(), serviceDate.atTime(1, 0), serviceDate);
        articleContentRepository.saveAndFlush(ArticleContent.of(articleRepository.findById(id).orElseThrow(), BODY));
        return id;
    }

    private Long activeQuiz(Long articleId, String question) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO quiz (article_id, question, choices, answer_seq, explanation)
            VALUES (?, ?, CAST(? AS JSONB), 1, '해설')
            RETURNING id
            """, Long.class, articleId, question,
            "[{\"seq\":1,\"content\":\"가나다라\"},{\"seq\":2,\"content\":\"마바사아\"},{\"seq\":3,\"content\":\"자차카타\"}]");
    }

    // 이 기사의 지금 퀴즈를 이미 받은 노선
    private Long assignedRouteQuiz(Long articleId) {
        Long userId = jdbcTemplate.queryForObject("""
            INSERT INTO users (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id
            """, Long.class, UUID.randomUUID().toString());
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at)
            VALUES (?, ?, 'SUNNY', 10, 'COMPLETED', 0, ?)
            RETURNING id
            """, Long.class, userId, serviceDate, serviceDate.plusDays(1).atTime(4, 0));
        Long quizId = jdbcTemplate.queryForObject(
            "SELECT id FROM quiz WHERE article_id = ? AND status = 'ACTIVE'", Long.class, articleId);
        return jdbcTemplate.queryForObject(
            "INSERT INTO route_quiz (route_id, quiz_id, quiz_order) VALUES (?, ?, 1) RETURNING id", Long.class,
            routeId, quizId);
    }

    private List<Map<String, Object>> quizzesOf(Long articleId) {
        return jdbcTemplate.queryForList("SELECT id, status FROM quiz WHERE article_id = ? ORDER BY id", articleId);
    }

    private static ArticleSummaryOutput summary(String title, ArticleDifficulty difficulty) {
        return new ArticleSummaryOutput(title, difficulty, "한 줄 요약입니다.",
            List.of(NEW_FIRST_SENTENCE, "둘째 문장입니다.", "셋째 문장입니다."), EVIDENCE, List.of("기준금리", "한국은행", "물가"));
    }

    private static QuizOutput quiz(String question) {
        return quiz(question, NEW_FIRST_SENTENCE);
    }

    private static QuizOutput quiz(String question, String evidence) {
        return new QuizOutput(evidence, question, "가나다라", List.of("마바사아", "자차카타"), "정답의 근거예요.");
    }
}
