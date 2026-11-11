package com.byarrival.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.route.entity.RecommendationLog;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.repository.ConditionHistoryRepository;
import com.byarrival.domain.route.repository.RecommendationLogRepository;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
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
 * 실제 DB·Redis와 보안 필터로 노선 생성, 후보 카드 담기·빼기, 시작하기·뒤로 가기, 카드 위치 저장을 확인한다.
 * 시각은 2026-11-10(화) 08:00 KST로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, RouteFlowIntegrationTest.FixedClockConfig.class})
class RouteFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 8, 0);
    private static final LocalDate TODAY = ServiceDay.of(NOW);

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
    private RouteRepository routeRepository;

    @Autowired
    private ConditionHistoryRepository conditionHistoryRepository;

    @Autowired
    private RecommendationLogRepository recommendationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = newCategory();
    }

    @Test
    void 노선을_만들고_카드를_담아_보딩패스까지_간다() throws Exception {
        // T=10분: 범위 7~12분. 2분짜리 HARD 4건·EASY 6건과 숨긴 기사 1건
        List<Long> articleIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            articleIds.add(newArticle(categoryId, "HARD", "2.0", "VISIBLE"));
        }
        for (int i = 0; i < 6; i++) {
            articleIds.add(newArticle(categoryId, "EASY", "2.0", "VISIBLE"));
        }
        Long hiddenId = newArticle(categoryId, "EASY", "2.0", "HIDDEN");
        Long userId = newUser(10, 0, categoryId);
        TokenResponse user = tokenService.issue(userId);

        String created = perform(user, post("/api/v1/routes"), "{\"condition\":\"SUNNY\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("CREATED"))
            .andExpect(jsonPath("$.data.route.condition").value("SUNNY"))
            .andExpect(jsonPath("$.data.route.targetMinutes").value(10))
            .andExpect(jsonPath("$.data.route.fallbackStep").value(0))
            .andExpect(jsonPath("$.data.route.expiresAt").value("2026-11-11T04:00:00"))
            .andExpect(jsonPath("$.data.route.cards.length()").value(10))
            .andExpect(jsonPath("$.data.route.cart.count").value(0))
            .andExpect(jsonPath("$.data.route.cart.minMinutes").value(7))
            .andExpect(jsonPath("$.data.route.cart.maxMinutes").value(12))
            .andExpect(jsonPath("$.data.route.cart.startable").value(false))
            .andReturn().getResponse().getContentAsString();
        Long routeId = ((Number) JsonPath.read(created, "$.data.route.routeId")).longValue();
        List<Integer> poolArticleIds = JsonPath.read(created, "$.data.route.cards[*].articleId");
        List<Boolean> recommended = JsonPath.read(created, "$.data.route.cards[*].recommended");
        assertThat(poolArticleIds).doesNotContain(hiddenId.intValue());
        assertThat(recommended.stream().filter(Boolean::booleanValue)).hasSize(5);

        // 오늘 이미 만든 노선이 있으면 컨디션을 바꿔도 같은 노선을 돌려준다
        perform(user, post("/api/v1/routes"), "{\"condition\":\"RAINY\"}")
            .andExpect(jsonPath("$.data.route.routeId").value(routeId))
            .andExpect(jsonPath("$.data.route.condition").value("SUNNY"));
        assertThat(conditionHistoryRepository.existsByRouteId(routeId)).isTrue();
        RecommendationLog log = recommendationLogRepository.findByRouteId(routeId).orElseThrow();
        assertThat(log.getCandidateCount()).isEqualTo(10);
        assertThat(log.getSelectedArticleIds()).hasSize(5);
        assertThat(log.getPoolSize()).isEqualTo(10);

        // 3장(6분)은 최소 시간 미달, 4장(8분)부터 시작할 수 있다. 같은 카드를 연타해도 한 번만 담긴다
        for (int i = 0; i < 3; i++) {
            addCard(user, routeId, poolArticleIds.get(i)).andExpect(status().isOk());
        }
        addCard(user, routeId, poolArticleIds.get(2))
            .andExpect(jsonPath("$.data.cart.count").value(3))
            .andExpect(jsonPath("$.data.cart.totalMinutes").value(6))
            .andExpect(jsonPath("$.data.cart.startable").value(false));
        addCard(user, routeId, poolArticleIds.get(3))
            .andExpect(jsonPath("$.data.cart.startable").value(true));

        // 6장(12분)까지 담고 7장째는 분량 초과
        addCard(user, routeId, poolArticleIds.get(4)).andExpect(status().isOk());
        addCard(user, routeId, poolArticleIds.get(5))
            .andExpect(jsonPath("$.data.cart.totalMinutes").value(12))
            .andExpect(jsonPath("$.data.cart.noMoreCards").value(true))
            .andExpect(jsonPath("$.data.cards[6].addable").value(false));
        addCard(user, routeId, poolArticleIds.get(6))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROUTE_CART_OVER_LIMIT"));

        // 빼면 담은 순서에서 빠진다
        perform(user, delete("/api/v1/routes/{routeId}/cart/{articleId}", routeId, poolArticleIds.get(0)), null)
            .andExpect(jsonPath("$.data.cart.count").value(5))
            .andExpect(jsonPath("$.data.cart.articleIds[0]").value(poolArticleIds.get(1)));

        // 시작하기 → 보딩패스. 담은 순서대로 보여 주고 추천 로그에 담은 카드를 남긴다
        perform(user, post("/api/v1/routes/{routeId}/boarding", routeId), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.articleCount").value(5))
            .andExpect(jsonPath("$.data.totalMinutes").value(10))
            .andExpect(jsonPath("$.data.cards[0].articleId").value(poolArticleIds.get(1)))
            .andExpect(jsonPath("$.data.cards[4].articleId").value(poolArticleIds.get(5)));
        assertThat(recommendationLogRepository.findByRouteId(routeId).orElseThrow().getCartArticleIds())
            .containsExactly(poolArticleIds.subList(1, 6).stream().map(Integer::longValue).toArray(Long[]::new));

        // 보딩패스 단계에서는 담기 불가, 시작하기를 다시 눌러도 같은 보딩패스
        addCard(user, routeId, poolArticleIds.get(0))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROUTE_INVALID_STATUS"));
        perform(user, post("/api/v1/routes/{routeId}/boarding", routeId), null)
            .andExpect(jsonPath("$.data.articleCount").value(5));
        perform(user, get("/api/v1/routes/today"), null)
            .andExpect(jsonPath("$.data.state").value("BOARDING"));

        // 뒤로 가면 담은 카드를 유지한 채 카드 선택 단계로 돌아간다
        perform(user, delete("/api/v1/routes/{routeId}/boarding", routeId), null)
            .andExpect(jsonPath("$.data.status").value("CREATED"))
            .andExpect(jsonPath("$.data.cart.count").value(5));
        perform(user, get("/api/v1/routes/today"), null)
            .andExpect(jsonPath("$.data.state").value("CREATED"))
            .andExpect(jsonPath("$.data.route.cart.count").value(5));
    }

    @Test
    void 마지막으로_본_카드_위치를_저장하고_복원한다() throws Exception {
        for (int i = 0; i < 3; i++) {
            newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        }
        TokenResponse user = tokenService.issue(newUser(10, 0, categoryId));
        Long routeId = createRoute(user);

        perform(user, put("/api/v1/routes/{routeId}/cursor", routeId), "{\"cardOrder\":2}")
            .andExpect(status().isOk());
        perform(user, get("/api/v1/routes/today"), null)
            .andExpect(jsonPath("$.data.route.lastViewedCardOrder").value(2));
        perform(user, put("/api/v1/routes/{routeId}/cursor", routeId), "{\"cardOrder\":99}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void 요일별_이동시간이_있으면_그_값을_목표_시간으로_쓴다() throws Exception {
        newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        TokenResponse user = tokenService.issue(newUser(10, 30, categoryId));

        perform(user, post("/api/v1/routes"), "{\"condition\":\"NORMAL\"}")
            .andExpect(jsonPath("$.data.route.targetMinutes").value(30))
            .andExpect(jsonPath("$.data.route.fallbackStep").value(2));
    }

    @Test
    void 담은_카드가_없으면_시작할_수_없다() throws Exception {
        newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        TokenResponse user = tokenService.issue(newUser(10, 0, categoryId));
        Long routeId = createRoute(user);

        perform(user, post("/api/v1/routes/{routeId}/boarding", routeId), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROUTE_NOT_STARTABLE"));
    }

    @Test
    void 추천_기사가_없으면_노선을_만들지_않고_로그만_남긴다() throws Exception {
        Long userId = newUser(10, 0, categoryId);
        TokenResponse user = tokenService.issue(userId);

        perform(user, post("/api/v1/routes"), "{\"condition\":\"CLOUDY\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("EMPTY"))
            .andExpect(jsonPath("$.data.route").doesNotExist());
        perform(user, get("/api/v1/routes/today"), null)
            .andExpect(jsonPath("$.data.state").value("NONE"));
        Integer logs = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM recommendation_log WHERE user_id = ? AND route_id IS NULL", Integer.class, userId);
        assertThat(logs).isEqualTo(1);
    }

    @Test
    void 이동시간이_없으면_노선을_만들_수_없다() throws Exception {
        TokenResponse user = tokenService.issue(newUser(null, 0, categoryId));

        perform(user, post("/api/v1/routes"), "{\"condition\":\"SUNNY\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROUTE_TRAVEL_TIME_NOT_SET"));
    }

    @Test
    void 다른_사람의_노선은_찾을_수_없다() throws Exception {
        Long articleId = newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        Long routeId = createRoute(tokenService.issue(newUser(10, 0, categoryId)));
        TokenResponse other = tokenService.issue(newUser(10, 0, categoryId));

        addCard(other, routeId, articleId.intValue())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    @Test
    void 후보_카드가_아니면_담을_수_없다() throws Exception {
        newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        Long otherArticleId = newArticle(newCategory(), "EASY", "3.0", "VISIBLE");
        TokenResponse user = tokenService.issue(newUser(10, 0, categoryId));
        Long routeId = createRoute(user);

        addCard(user, routeId, otherArticleId.intValue())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROUTE_CARD_NOT_IN_POOL"));
    }

    @Test
    void 만료된_노선에는_담을_수_없다() throws Exception {
        Long articleId = newArticle(categoryId, "EASY", "3.0", "VISIBLE");
        Long userId = newUser(10, 0, categoryId);
        LocalDate yesterday = TODAY.minusDays(1);
        Route route = routeRepository.save(Route.builder()
            .userId(userId)
            .serviceDate(yesterday)
            .condition(RouteCondition.SUNNY)
            .targetMinutes(10)
            .fallbackStep(0)
            .expiresAt(ServiceDay.endOf(yesterday))
            .build());

        addCard(tokenService.issue(userId), route.getId(), articleId.intValue())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROUTE_EXPIRED"));
    }

    private Long createRoute(TokenResponse user) throws Exception {
        String body = perform(user, post("/api/v1/routes"), "{\"condition\":\"SUNNY\"}")
            .andExpect(jsonPath("$.data.state").value("CREATED"))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.route.routeId")).longValue();
    }

    private ResultActions addCard(TokenResponse user, Long routeId, Integer articleId) throws Exception {
        return perform(user, put("/api/v1/routes/{routeId}/cart/{articleId}", routeId, articleId), null);
    }

    private ResultActions perform(TokenResponse user, MockHttpServletRequestBuilder request, String body)
        throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Long newUser(Integer defaultMinutes, int tuesdayMinutes, Long interestCategoryId) {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        // 요일별 설정은 시작 시각과 함께 저장한다. 0분은 설정하지 않은 요일(쉬는 날)로 둔다
        Integer tuesday = tuesdayMinutes == 0 ? null : tuesdayMinutes;
        jdbcTemplate.update("UPDATE users SET default_commute_minutes = ?, tue_minutes = ?, "
                + "tue_start_time = CASE WHEN CAST(? AS INTEGER) IS NULL THEN NULL ELSE TIME '08:00' END WHERE id = ?",
            defaultMinutes, tuesday, tuesday, userId);
        jdbcTemplate.update("INSERT INTO user_interest_category (user_id, category_id) VALUES (?, ?)",
            userId, interestCategoryId);
        return userId;
    }

    private Long newCategory() {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Long newArticle(Long categoryId, String difficulty, String readingMinutes, String visibility) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', ?, ?, ?, ?::numeric, '첫 문장', '둘째 문장', '셋째 문장',
                '한 줄 요약', 'READY', TRUE, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            NOW.minusHours(10), TODAY, difficulty, readingMinutes, visibility);
    }
}
