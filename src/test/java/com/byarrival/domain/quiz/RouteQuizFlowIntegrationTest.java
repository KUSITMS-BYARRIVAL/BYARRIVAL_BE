package com.byarrival.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 오늘의 퀴즈(QUIZ-01~03)를 실제 DB와 보안 필터로 확인한다. 시각은 2026-11-12(목) 09:00 KST로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, RouteQuizFlowIntegrationTest.FixedClockConfig.class})
class RouteQuizFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 9, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private Long categoryId;
    private String categoryName;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryName = "테스트-" + UUID.randomUUID().toString().substring(0, 8);
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class, categoryName);
    }

    @Test
    void 완독한_기사로_3문제를_고정해_내고_답은_한_번만_저장한다() throws Exception {
        Long userId = newUser();
        TokenResponse user = tokenService.issue(userId);
        // 5건 모두 읽었고, 그중 4건만 퀴즈가 있다. 정답은 모두 2번
        List<Long> articles = new ArrayList<>();
        List<Long> quizzes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Long articleId = newArticle("기사 " + i);
            articles.add(articleId);
            if (i != 3) {
                quizzes.add(newQuiz(articleId, "문제 " + i + "?"));
            }
        }
        Long routeId = newRoute(userId, "COMPLETED", articles, articles.size());

        String first = perform(user, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progress.total").value(3))
            .andExpect(jsonPath("$.data.progress.answeredCount").value(0))
            .andExpect(jsonPath("$.data.progress.finished").value(false))
            .andExpect(jsonPath("$.data.nextQuizOrder").value(1))
            .andExpect(jsonPath("$.data.quizzes[0].quizOrder").value(1))
            .andExpect(jsonPath("$.data.quizzes[0].categoryId").value(categoryId))
            .andExpect(jsonPath("$.data.quizzes[0].categoryName").value(categoryName))
            .andExpect(jsonPath("$.data.quizzes[0].choices.length()").value(3))
            .andExpect(jsonPath("$.data.quizzes[0].choices[0].seq").value(1))
            .andExpect(jsonPath("$.data.quizzes[0].choices[0].content").value("가"))
            .andExpect(jsonPath("$.data.quizzes[0].answered").value(false))
            // 풀기 전에는 정답과 해설을 내려 주지 않는다
            .andExpect(jsonPath("$.data.quizzes[0].answerSeq").doesNotExist())
            .andExpect(jsonPath("$.data.quizzes[0].explanation").doesNotExist())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> quizIds = JsonPath.read(first, "$.data.quizzes[*].quizId");
        List<Integer> articleIds = JsonPath.read(first, "$.data.quizzes[*].articleId");
        List<String> titles = JsonPath.read(first, "$.data.quizzes[*].articleTitle");
        assertThat(quizIds).hasSize(3).allMatch(id -> quizzes.contains(id.longValue()));
        // 정거장 순서대로 낸다
        assertThat(articleIds).isSortedAccordingTo(Integer::compare);
        assertThat(titles).allMatch(title -> title.startsWith("기사 "));

        // 다시 열어도 같은 문제다
        String again = perform(user, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> againIds = JsonPath.read(again, "$.data.quizzes[*].quizId");
        assertThat(againIds).isEqualTo(quizIds);

        long firstQuiz = quizIds.get(0);
        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, firstQuiz),
            "{\"choiceSeq\":1}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quizId").value(firstQuiz))
            .andExpect(jsonPath("$.data.selectedSeq").value(1))
            .andExpect(jsonPath("$.data.answerSeq").value(2))
            .andExpect(jsonPath("$.data.correct").value(false))
            .andExpect(jsonPath("$.data.explanation").value("요약에 나와요."))
            .andExpect(jsonPath("$.data.progress.answeredCount").value(1))
            .andExpect(jsonPath("$.data.progress.correctCount").value(0));
        // 다시 제출해도 처음 답이 남는다
        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, firstQuiz),
            "{\"choiceSeq\":2}")
            .andExpect(jsonPath("$.data.selectedSeq").value(1))
            .andExpect(jsonPath("$.data.correct").value(false))
            .andExpect(jsonPath("$.data.progress.answeredCount").value(1));
        // 없는 선택지와 노선에 없는 퀴즈
        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, quizIds.get(1)),
            "{\"choiceSeq\":4}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("QUIZ_CHOICE_NOT_FOUND"));
        Long notPicked = quizzes.stream().filter(id -> !quizIds.contains(id.intValue())).findFirst().orElseThrow();
        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, notPicked),
            "{\"choiceSeq\":1}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));

        // 나갔다 다시 들어오면 2번부터 이어서 푼다
        perform(user, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(jsonPath("$.data.nextQuizOrder").value(2))
            .andExpect(jsonPath("$.data.quizzes[0].answered").value(true))
            .andExpect(jsonPath("$.data.quizzes[0].selectedSeq").value(1))
            .andExpect(jsonPath("$.data.quizzes[0].correct").value(false))
            .andExpect(jsonPath("$.data.quizzes[0].answerSeq").value(2));

        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, quizIds.get(1)),
            "{\"choiceSeq\":2}")
            .andExpect(jsonPath("$.data.correct").value(true));
        perform(user, post("/api/v1/routes/{routeId}/quizzes/{quizId}/answers", routeId, quizIds.get(2)),
            "{\"choiceSeq\":2}")
            .andExpect(jsonPath("$.data.progress.finished").value(true));

        perform(user, get("/api/v1/routes/{routeId}/quizzes/result", routeId), null)
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.answeredCount").value(3))
            .andExpect(jsonPath("$.data.correctCount").value(2))
            .andExpect(jsonPath("$.data.finished").value(true));
        perform(user, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(jsonPath("$.data.nextQuizOrder").doesNotExist());

        // 다른 사용자의 노선은 없는 것으로 본다
        TokenResponse other = tokenService.issue(newUser());
        perform(other, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    void 읽은_기사가_3건보다_적으면_읽은_만큼만_내고_퀴즈가_없으면_0문제다() throws Exception {
        Long userId = newUser();
        TokenResponse user = tokenService.issue(userId);
        Long read = newArticle("읽은 기사");
        Long unread = newArticle("안 읽은 기사");
        newQuiz(read, "읽은 문제?");
        newQuiz(unread, "안 읽은 문제?");
        Long routeId = newRoute(userId, "COMPLETED", List.of(read, unread), 1);

        perform(user, get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(jsonPath("$.data.progress.total").value(1))
            .andExpect(jsonPath("$.data.quizzes[0].articleId").value(read));
        // 안 푼 문제가 있으면 결과는 끝나지 않은 상태다
        perform(user, get("/api/v1/routes/{routeId}/quizzes/result", routeId), null)
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.answeredCount").value(0))
            .andExpect(jsonPath("$.data.finished").value(false));

        Long noQuizUser = newUser();
        Long noQuizRoute = newRoute(noQuizUser, "COMPLETED", List.of(newArticle("퀴즈 없는 기사")), 1);
        perform(tokenService.issue(noQuizUser), get("/api/v1/routes/{routeId}/quizzes", noQuizRoute), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progress.total").value(0))
            .andExpect(jsonPath("$.data.quizzes.length()").value(0));
    }

    @Test
    void 도착하기_전에는_퀴즈를_풀_수_없다() throws Exception {
        Long userId = newUser();
        Long article = newArticle("운행 중 기사");
        newQuiz(article, "문제?");
        Long routeId = newRoute(userId, "IN_PROGRESS", List.of(article), 1);

        perform(tokenService.issue(userId), get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROUTE_INVALID_STATUS"));
    }

    @Test
    void 서비스일이_끝난_노선의_퀴즈는_풀_수_없다() throws Exception {
        Long userId = newUser();
        Long article = newArticle("어제 기사");
        newQuiz(article, "문제?");
        Long routeId = newRoute(userId, "COMPLETED", List.of(article), 1);
        jdbcTemplate.update("UPDATE route SET expires_at = ? WHERE id = ?", TODAY.atTime(4, 0), routeId);

        perform(tokenService.issue(userId), get("/api/v1/routes/{routeId}/quizzes", routeId), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROUTE_EXPIRED"));
    }

    private ResultActions perform(TokenResponse token, MockHttpServletRequestBuilder request, String body)
        throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Long newUser() {
        return userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
    }

    // 담은 순서대로 정거장이 되고, 앞에서부터 read건을 읽었다
    private Long newRoute(Long userId, String status, List<Long> articleIds, int read) {
        boolean completed = status.equals("COMPLETED");
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                started_at, completed_at)
            VALUES (?, ?, 'SUNNY', 10, ?, 0, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, TODAY, status, TODAY.plusDays(1).atTime(4, 0), TODAY.atTime(8, 0),
            completed ? TODAY.atTime(8, 30) : null);
        for (int i = 0; i < articleIds.size(); i++) {
            jdbcTemplate.update("""
                INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                    cart_order, carted_at, read_at)
                VALUES (?, ?, ?, 'EASY', 2.0, TRUE, ?, ?, ?)
                """, routeId, articleIds.get(i), i + 1, i + 1, TODAY.atTime(7, 50),
                i < read ? TODAY.atTime(8, i + 1) : null);
        }
        return routeId;
    }

    private Long newArticle(String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', TRUE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            TODAY.atTime(1, 0), TODAY);
    }

    private Long newQuiz(Long articleId, String question) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO quiz (article_id, question, choices, answer_seq, explanation)
            VALUES (?, ?, '[{"seq":1,"content":"가"},{"seq":2,"content":"나"},{"seq":3,"content":"다"}]'::jsonb, 2,
                '요약에 나와요.')
            RETURNING id
            """, Long.class, articleId, question);
    }
}
