package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB와 보안 필터로 어드민 기사 목록·상세·숨김·복구를 확인한다. 테스트마다 롤백하고 언론사 이름으로 데이터를 구분한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminArticleFlowIntegrationTest {

    private static final String ARTICLES = "/api/admin/articles";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private static final LocalDate SERVICE_DATE = LocalDate.of(2031, 3, 5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleQueryService articleQueryService;

    private Cookie cookie;
    private String email;
    private String press;
    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        email = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", email, PASSWORD_HASH);
        String setCookie = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        cookie = new Cookie(AdminCookieFactory.COOKIE_NAME, pair.substring(pair.indexOf('=') + 1));
        press = "언론-" + UUID.randomUUID().toString().substring(0, 8);
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES (?, ?) RETURNING id", Long.class, press,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    void 탭별_건수와_상태를_나눠_보여_준다() throws Exception {
        Long exposed = readyArticle("노출 기사", true);
        Long noQuiz = readyArticle("퀴즈 실패 기사", false);
        Long noSummary = cleanedArticle("요약 실패 기사");
        Long hidden = readyArticle("숨긴 기사", true);
        jdbcTemplate.update("UPDATE article SET visibility = 'HIDDEN' WHERE id = ?", hidden);
        Long duplicate = readyArticle("중복 기사", true);
        jdbcTemplate.update("UPDATE article SET recommendable = FALSE, duplicate_of_article_id = ? WHERE id = ?",
            exposed, duplicate);

        list("").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.counts.all").value(5))
            .andExpect(jsonPath("$.data.counts.exposed").value(1))
            .andExpect(jsonPath("$.data.counts.hidden").value(1))
            .andExpect(jsonPath("$.data.counts.error").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(5));

        list("&tab=ERROR")
            .andExpect(jsonPath("$.data.articles.length()").value(2))
            .andExpect(jsonPath("$.data.articles[0].articleId").value(noSummary))
            .andExpect(jsonPath("$.data.articles[0].steps.summary").value("FAILED"))
            .andExpect(jsonPath("$.data.articles[0].steps.quiz").value("WAITING"))
            .andExpect(jsonPath("$.data.articles[1].articleId").value(noQuiz))
            .andExpect(jsonPath("$.data.articles[1].steps.summary").value("SUCCESS"))
            .andExpect(jsonPath("$.data.articles[1].steps.quiz").value("FAILED"));
    }

    @Test
    void 배치가_처리_중인_기사는_오류로_세지_않는다() throws Exception {
        cleanedArticle("처리 중 기사");
        jdbcTemplate.update("INSERT INTO batch_run (service_date, status, started_at) VALUES (?, 'RUNNING', ?)",
            SERVICE_DATE, LocalDateTime.now());

        list("")
            .andExpect(jsonPath("$.data.counts.error").value(0))
            .andExpect(jsonPath("$.data.articles[0].state").value("PROCESSING"))
            .andExpect(jsonPath("$.data.articles[0].steps.summary").value("WAITING"));
    }

    @Test
    void 제목과_조건으로_검색하고_검색어의_특수문자는_그대로_찾는다() throws Exception {
        readyArticle("금리 100% 동결", true);
        readyArticle("금리 인상", true);

        mockMvc.perform(get(ARTICLES).param("pressName", press).param("keyword", "100%").cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.articles[0].title").value("금리 100% 동결"));
        list("&categoryId=" + categoryId + "&difficulty=EASY&processStatus=READY")
            .andExpect(jsonPath("$.data.totalElements").value(2));
        list("&difficulty=HARD").andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 수집일로_거르고_시작일이_끝일보다_늦으면_400이다() throws Exception {
        Long article = readyArticle("어제 수집", true);
        jdbcTemplate.update("UPDATE article SET created_at = ? WHERE id = ?", LocalDateTime.of(2031, 3, 4, 23, 59),
            article);

        list("&collectedFrom=2031-03-04&collectedTo=2031-03-04").andExpect(jsonPath("$.data.totalElements").value(1));
        list("&collectedFrom=2031-03-05").andExpect(jsonPath("$.data.totalElements").value(0));
        list("&collectedFrom=2031-03-05&collectedTo=2031-03-04").andExpect(status().isBadRequest());
    }

    @Test
    void 한_페이지는_20개이고_다음_페이지를_알려_준다() throws Exception {
        for (int i = 0; i < 21; i++) {
            readyArticle("기사 " + i, true);
        }

        list("").andExpect(jsonPath("$.data.articles.length()").value(20))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true));
        list("&page=2").andExpect(jsonPath("$.data.articles.length()").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false));
        list("&page=0").andExpect(status().isBadRequest());
    }

    @Test
    void 조회_수는_담은_사용자_수와_원문을_연_사용자_수다() throws Exception {
        Long article = readyArticle("담은 기사", true);
        carted(article, true);
        carted(article, false);

        list("")
            .andExpect(jsonPath("$.data.articles[0].viewCount").value(2))
            .andExpect(jsonPath("$.data.articles[0].readCount").value(1));
        mockMvc.perform(get(ARTICLES + "/" + article).cookie(cookie))
            .andExpect(jsonPath("$.data.viewCount").value(2))
            .andExpect(jsonPath("$.data.readCount").value(1));
    }

    @Test
    void 상세는_요약_퀴즈_처리_상태를_돌려준다() throws Exception {
        Long article = readyArticle("상세 기사", true);

        mockMvc.perform(get(ARTICLES + "/" + article).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("상세 기사"))
            .andExpect(jsonPath("$.data.summary.length()").value(3))
            .andExpect(jsonPath("$.data.readingMinutes").value(2))
            .andExpect(jsonPath("$.data.quiz.question").value("문제"))
            .andExpect(jsonPath("$.data.quiz.choices.length()").value(3))
            .andExpect(jsonPath("$.data.quiz.answerSeq").value(1))
            .andExpect(jsonPath("$.data.steps.quiz").value("SUCCESS"));
    }

    @Test
    void 없는_기사는_404다() throws Exception {
        mockMvc.perform(get(ARTICLES + "/999999999").cookie(cookie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADMIN_ARTICLE_NOT_FOUND"));
    }

    @Test
    void 숨기면_앱에서_빠지고_복구하면_돌아오며_이력이_남는다() throws Exception {
        Long article = readyArticle("숨길 기사", true);

        postJson(ARTICLES + "/" + article + "/hide", "{\"reason\":\"CORRECTION\",\"memo\":\" 정정 요청 \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("HIDDEN"));
        assertThat(articleQueryService.findUnavailableIds(List.of(article))).containsExactly(article);

        mockMvc.perform(post(ARTICLES + "/" + article + "/restore").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("EXPOSED"));
        assertThat(articleQueryService.findUnavailableIds(List.of(article))).isEmpty();

        mockMvc.perform(get(ARTICLES + "/" + article).cookie(cookie))
            .andExpect(jsonPath("$.data.histories.length()").value(2))
            .andExpect(jsonPath("$.data.histories[0].action").value("RESTORE"))
            .andExpect(jsonPath("$.data.histories[1].action").value("HIDE"))
            .andExpect(jsonPath("$.data.histories[1].reason").value("CORRECTION"))
            .andExpect(jsonPath("$.data.histories[1].memo").value("정정 요청"))
            .andExpect(jsonPath("$.data.histories[1].adminEmail").value(email));
    }

    @Test
    void 같은_기사를_다시_숨겨도_이력은_한_번만_남는다() throws Exception {
        Long article = readyArticle("숨길 기사", true);

        postJson(ARTICLES + "/" + article + "/hide", "{\"reason\":\"QUALITY\"}").andExpect(status().isOk());
        postJson(ARTICLES + "/" + article + "/hide", "{\"reason\":\"QUALITY\"}").andExpect(status().isOk());

        assertThat(historyCount(article)).isEqualTo(1);
    }

    @Test
    void 사유가_없거나_기타인데_메모가_없으면_400이다() throws Exception {
        Long article = readyArticle("숨길 기사", true);

        postJson(ARTICLES + "/" + article + "/hide", "{}").andExpect(status().isBadRequest());
        postJson(ARTICLES + "/" + article + "/hide", "{\"reason\":\"ETC\",\"memo\":\"  \"}")
            .andExpect(status().isBadRequest());
        postJson(ARTICLES + "/" + article + "/hide",
            "{\"reason\":\"QUALITY\",\"memo\":\"%s\"}".formatted("가".repeat(101)))
            .andExpect(status().isBadRequest());
        assertThat(historyCount(article)).isZero();
    }

    @Test
    void 원문이_삭제된_기사는_숨기거나_복구할_수_없다() throws Exception {
        Long article = readyArticle("삭제된 기사", true);
        jdbcTemplate.update("UPDATE article SET visibility = 'DELETED' WHERE id = ?", article);

        postJson(ARTICLES + "/" + article + "/hide", "{\"reason\":\"QUALITY\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_ARTICLE_UNAVAILABLE"));
        mockMvc.perform(post(ARTICLES + "/" + article + "/restore").cookie(cookie))
            .andExpect(status().isConflict());
    }

    @Test
    void 일괄_숨김과_복구는_바꿀_수_없는_기사를_제외하고_결과를_알려_준다() throws Exception {
        Long first = readyArticle("기사 1", true);
        Long second = readyArticle("기사 2", true);
        Long deleted = readyArticle("삭제 기사", true);
        jdbcTemplate.update("UPDATE article SET visibility = 'DELETED' WHERE id = ?", deleted);

        postJson(ARTICLES + "/bulk-hide", "{\"articleIds\":[%d,%d,%d,%d,999999999],\"reason\":\"COPYRIGHT\"}"
            .formatted(first, second, second, deleted))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changedIds.length()").value(2))
            .andExpect(jsonPath("$.data.skippedIds.length()").value(2));
        assertThat(historyCount(second)).isEqualTo(1);

        postJson(ARTICLES + "/bulk-restore", "{\"articleIds\":[%d,%d]}".formatted(first, deleted))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changedIds[0]").value(first))
            .andExpect(jsonPath("$.data.skippedIds[0]").value(deleted));
    }

    @Test
    void 쿠키_없이는_기사_API를_쓸_수_없다() throws Exception {
        mockMvc.perform(get(ARTICLES)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ARTICLES + "/1/hide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"QUALITY\"}"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get(ARTICLES + "?pressName=" + press + query).cookie(cookie));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int historyCount(Long articleId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM article_visibility_history WHERE article_id = ?",
            Integer.class, articleId);
    }

    private Long readyArticle(String title, boolean withQuiz) {
        Long article = jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line,
                process_status, recommendable)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'EASY', 1.5, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약', 'READY', TRUE)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title, press,
            SERVICE_DATE.atTime(3, 0), SERVICE_DATE);
        if (withQuiz) {
            jdbcTemplate.update("""
                INSERT INTO quiz (article_id, question, choices, answer_seq, explanation)
                VALUES (?, '문제', '[{"seq":1,"content":"정답"},{"seq":2,"content":"오답"},{"seq":3,"content":"오답2"}]'::jsonb,
                    1, '해설')
                """, article);
        }
        return article;
    }

    private Long cleanedArticle(String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                reading_minutes, process_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1.5, 'CLEANED')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title, press,
            SERVICE_DATE.atTime(3, 0), SERVICE_DATE);
    }

    private void carted(Long articleId, boolean read) {
        Long userId = jdbcTemplate.queryForObject(
            "INSERT INTO users (provider, provider_user_id) VALUES ('KAKAO', ?) RETURNING id", Long.class,
            UUID.randomUUID().toString());
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at)
            VALUES (?, ?, 'SUNNY', 10, 'IN_PROGRESS', 0, ?)
            RETURNING id
            """, Long.class, userId, SERVICE_DATE, SERVICE_DATE.plusDays(1).atTime(4, 0));
        jdbcTemplate.update("""
            INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                cart_order, carted_at, read_at)
            VALUES (?, ?, 1, 'EASY', 2.0, TRUE, 1, ?, ?)
            """, routeId, articleId, SERVICE_DATE.atTime(8, 0), read ? SERVICE_DATE.atTime(8, 10) : null);
    }
}
