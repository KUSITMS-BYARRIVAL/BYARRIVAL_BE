package com.byarrival.domain.stat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 나의 기록(STAT-01~03)과 앱 접속 기록을 실제 DB로 확인한다. 시각은 2026-11-12(목) 08:00 KST로 고정한다.
 * 데이터는 테스트마다 새 사용자로 나눈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, StatFlowIntegrationTest.FixedClockConfig.class})
class StatFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 8, 0);

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
    private Long userId;
    private TokenResponse token;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
        userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        token = tokenService.issue(userId);
    }

    @Test
    void 기간별로_이용시간과_읽은_기사_완주를_센다() throws Exception {
        // 11-10(화) 완주: 08:00 출발, 08:12:30 도착(13분), 2건 완독
        newRoute(LocalDate.of(2026, 11, 10), 2, 2, LocalDateTime.of(2026, 11, 10, 8, 12, 30));
        // 11-11(수) 운행 중 만료: 3건 담고 1건 완독
        newRoute(LocalDate.of(2026, 11, 11), 3, 1, null);
        // 10-01 완주: 5분, 1건 완독
        newRoute(LocalDate.of(2026, 10, 1), 1, 1, LocalDateTime.of(2026, 10, 1, 8, 4, 10));

        perform(get("/api/v1/stats/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.period").value("WEEK"))
            .andExpect(jsonPath("$.data.from").value("2026-11-09"))
            .andExpect(jsonPath("$.data.to").value("2026-11-12"))
            .andExpect(jsonPath("$.data.usedMinutes").value(13))
            .andExpect(jsonPath("$.data.readArticleCount").value(3))
            .andExpect(jsonPath("$.data.completedCount").value(1))
            .andExpect(jsonPath("$.data.empty").value(false));
        perform(get("/api/v1/stats/summary").param("period", "MONTH"))
            .andExpect(jsonPath("$.data.from").value("2026-11-01"))
            .andExpect(jsonPath("$.data.readArticleCount").value(3));
        perform(get("/api/v1/stats/summary").param("period", "YEAR"))
            .andExpect(jsonPath("$.data.from").value("2026-01-01"))
            .andExpect(jsonPath("$.data.usedMinutes").value(18))
            .andExpect(jsonPath("$.data.readArticleCount").value(4));
        perform(get("/api/v1/stats/summary").param("period", "ALL"))
            .andExpect(jsonPath("$.data.from").doesNotExist())
            .andExpect(jsonPath("$.data.usedMinutes").value(18))
            .andExpect(jsonPath("$.data.readArticleCount").value(4))
            .andExpect(jsonPath("$.data.completedCount").value(2));

        String graph = perform(get("/api/v1/stats/weekly"))
            .andExpect(jsonPath("$.data.weeks.length()").value(6))
            .andExpect(jsonPath("$.data.weeks[0].weekStart").value("2026-10-05"))
            .andExpect(jsonPath("$.data.weeks[5].weekStart").value("2026-11-09"))
            .andExpect(jsonPath("$.data.weeks[5].weekEnd").value("2026-11-15"))
            .andExpect(jsonPath("$.data.weeks[5].usedMinutes").value(13))
            .andExpect(jsonPath("$.data.weeks[5].readArticleCount").value(3))
            .andExpect(jsonPath("$.data.weeks[5].completedCount").value(1))
            .andExpect(jsonPath("$.data.empty").value(false))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> earlierReads = JsonPath.read(graph, "$.data.weeks[0:5].readArticleCount");
        assertThat(earlierReads).containsOnly(0);
    }

    @Test
    void 기록이_없으면_비어_있다() throws Exception {
        perform(get("/api/v1/stats/summary").param("period", "ALL"))
            .andExpect(jsonPath("$.data.usedMinutes").value(0))
            .andExpect(jsonPath("$.data.empty").value(true));
        perform(get("/api/v1/stats/weekly")).andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    void 잔디는_평일만_날짜별_단계를_네_가지로_보여_준다() throws Exception {
        newRoute(LocalDate.of(2026, 11, 10), 2, 2, LocalDateTime.of(2026, 11, 10, 8, 12));
        newRoute(LocalDate.of(2026, 11, 11), 3, 1, null);
        // 노선은 만들었지만 하나도 읽지 않은 날
        newRoute(LocalDate.of(2026, 11, 9), 2, 0, null);
        // 앱만 열어 본 날(KST 자정 기준 날짜). 11-06(금)은 평일, 11-08(일)은 주말이라 잔디에 없다
        for (LocalDate date : List.of(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8))) {
            jdbcTemplate.update("INSERT INTO usage_event_log (user_id, event_type, service_date, occurred_at) "
                + "VALUES (?, 'APP_ACCESS', ?, ?)", userId, date, date.atTime(21, 0));
        }

        String body = perform(get("/api/v1/stats/calendar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.year").value(2026))
            .andExpect(jsonPath("$.data.today").value("2026-11-12"))
            // 2026년 평일 261일
            .andExpect(jsonPath("$.data.days.length()").value(261))
            .andExpect(jsonPath("$.data.completedDays").value(1))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> days = JsonPath.read(body, "$.data.days");
        assertThat(days).noneMatch(day -> {
            DayOfWeek dayOfWeek = LocalDate.parse((String) day.get("date")).getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        });
        assertThat(day(days, "2026-11-05").get("level")).isEqualTo("NONE");
        assertThat(day(days, "2026-11-06").get("level")).isEqualTo("ACCESSED");
        assertThat(day(days, "2026-11-09").get("level")).isEqualTo("ACCESSED");
        assertThat(day(days, "2026-11-10").get("level")).isEqualTo("COMPLETED");
        assertThat(day(days, "2026-11-10").get("readCount")).isEqualTo(2);
        assertThat(day(days, "2026-11-11").get("level")).isEqualTo("PARTIAL");
        // 이 조회 자체가 오늘 앱 접속으로 남는다
        assertThat(day(days, "2026-11-12").get("level")).isEqualTo("ACCESSED");
        assertThat(day(days, "2026-11-12").get("future")).isEqualTo(false);
        assertThat(day(days, "2026-11-13").get("future")).isEqualTo(true);

        perform(get("/api/v1/stats/calendar").param("year", "2025"))
            .andExpect(jsonPath("$.data.days.length()").value(261))
            .andExpect(jsonPath("$.data.days[0].date").value("2025-01-01"))
            .andExpect(jsonPath("$.data.completedDays").value(0));
        perform(get("/api/v1/stats/calendar").param("year", "1999"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 연속_완주는_주말을_건너뛰고_평일만_센다() throws Exception {
        // 10-19(월)~10-23(금), 10-26(월)~10-28(수): 주말을 건너뛰어 8일 연속. 10-29(목)에 끊김
        for (LocalDate day : List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21),
            LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 26),
            LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28))) {
            newRoute(day, 1, 1, day.atTime(8, 10));
        }
        // 11-06(금), 11-07(토, 주말이라 세지 않음), 11-09(월)~11-11(수). 오늘 11-12(목)는 아직 완주 전
        for (LocalDate day : List.of(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11))) {
            newRoute(day, 1, 1, day.atTime(8, 10));
        }

        perform(get("/api/v1/stats/streak"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStreak").value(4))
            .andExpect(jsonPath("$.data.longestStreak").value(8));
    }

    @Test
    void 컨디션_기록은_올해_완주한_날만_센다() throws Exception {
        newRoute(LocalDate.of(2026, 3, 2), 1, 1, LocalDate.of(2026, 3, 2).atTime(8, 10), "RAINY");
        newRoute(LocalDate.of(2026, 11, 2), 1, 1, LocalDate.of(2026, 11, 2).atTime(8, 10), "SUNNY");
        newRoute(LocalDate.of(2026, 11, 3), 1, 1, LocalDate.of(2026, 11, 3).atTime(8, 10), "CLOUDY");
        newRoute(LocalDate.of(2026, 11, 4), 1, 1, LocalDate.of(2026, 11, 4).atTime(8, 10), "CLOUDY");
        // 완주하지 않은 날과 작년은 세지 않는다
        newRoute(LocalDate.of(2026, 11, 5), 2, 1, null, "SUNNY");
        newRoute(LocalDate.of(2025, 12, 1), 1, 1, LocalDate.of(2025, 12, 1).atTime(8, 10), "NORMAL");

        perform(get("/api/v1/stats/conditions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.year").value(2026))
            .andExpect(jsonPath("$.data.yearCounts.sunny").value(1))
            .andExpect(jsonPath("$.data.yearCounts.normal").value(0))
            .andExpect(jsonPath("$.data.yearCounts.cloudy").value(2))
            .andExpect(jsonPath("$.data.yearCounts.rainy").value(1))
            .andExpect(jsonPath("$.data.monthCounts.rainy").value(0))
            .andExpect(jsonPath("$.data.monthTopCondition").value("CLOUDY"));
    }

    @Test
    void 이번_달_완주가_없으면_가장_많은_컨디션은_없다() throws Exception {
        perform(get("/api/v1/stats/conditions"))
            .andExpect(jsonPath("$.data.yearCounts.sunny").value(0))
            .andExpect(jsonPath("$.data.monthTopCondition").doesNotExist());
        perform(get("/api/v1/stats/streak"))
            .andExpect(jsonPath("$.data.currentStreak").value(0))
            .andExpect(jsonPath("$.data.longestStreak").value(0));
    }

    @Test
    void 같은_날_여러_번_접속해도_접속_기록은_하루_한_번이다() throws Exception {
        perform(get("/api/v1/stats/summary"));
        perform(get("/api/v1/stats/weekly"));

        Integer accessCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM usage_event_log WHERE user_id = ? AND event_type = 'APP_ACCESS'", Integer.class,
            userId);
        LocalDate accessDate = jdbcTemplate.queryForObject(
            "SELECT service_date FROM usage_event_log WHERE user_id = ? AND event_type = 'APP_ACCESS'",
            LocalDate.class, userId);
        assertThat(accessCount).isEqualTo(1);
        assertThat(accessDate).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    private static Map<String, Object> day(List<Map<String, Object>> days, String date) {
        return days.stream().filter(day -> date.equals(day.get("date"))).findFirst().orElseThrow();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()));
    }

    // carted건을 담고 그중 read건을 읽은 노선. completedAt이 있으면 08:00에 출발해 그 시각에 완주했다
    private void newRoute(LocalDate serviceDate, int carted, int read, LocalDateTime completedAt) {
        newRoute(serviceDate, carted, read, completedAt, "SUNNY");
    }

    private void newRoute(LocalDate serviceDate, int carted, int read, LocalDateTime completedAt, String condition) {
        String status = completedAt != null ? "COMPLETED" : read > 0 ? "IN_PROGRESS" : "CREATED";
        LocalDateTime startedAt = status.equals("CREATED") ? null : serviceDate.atTime(8, 0);
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                started_at, completed_at)
            VALUES (?, ?, ?, 10, ?, 0, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, serviceDate, condition, status, serviceDate.plusDays(1).atTime(4, 0), startedAt,
            completedAt);
        for (int i = 1; i <= carted; i++) {
            Long articleId = newArticle(serviceDate);
            jdbcTemplate.update("""
                INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                    cart_order, carted_at, read_at)
                VALUES (?, ?, ?, 'EASY', 2.0, TRUE, ?, ?, ?)
                """, routeId, articleId, i, i, serviceDate.atTime(7, 50),
                i <= read ? serviceDate.atTime(8, i) : null);
        }
    }

    private Long newArticle(LocalDate serviceDate) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장',
                '한 줄 요약', 'READY', TRUE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            serviceDate.atTime(0, 0), serviceDate);
    }
}
