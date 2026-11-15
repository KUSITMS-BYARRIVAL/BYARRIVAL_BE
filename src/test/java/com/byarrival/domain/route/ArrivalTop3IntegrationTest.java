package com.byarrival.domain.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 도착하기 응답의 많이 담은 뉴스 TOP3 항목(ARR-02). 집계 최소 인원을 1명으로 낮춰 순위가 나오는 경우를 본다.
 * 서비스일은 다른 테스트와 겹치지 않는 2033-04-05다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "route.popular.min-users=1")
@Import({TestcontainersConfig.class, ArrivalTop3IntegrationTest.FixedClockConfig.class})
class ArrivalTop3IntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2033, 4, 5);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(9, 0).atZone(KST).toInstant(), KST);
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

    @Test
    void TOP3_항목에_예상_읽기시간을_분으로_내려주고_없으면_null이다() throws Exception {
        Long feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        Long categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
        Long mine = newArticle(feedId, categoryId, "2.0");
        Long popular = newArticle(feedId, categoryId, "2.3");
        // 읽기시간을 계산하지 못한 기사(원문을 못 가져와 추천 후보가 아닌 기사)도 담겨 있을 수 있다
        Long noMinutes = newArticleWithoutMinutes(feedId, categoryId);
        Long me = newUser();
        // 나는 mine을 다 읽었고, 다른 사용자 둘은 popular를, 한 명은 noMinutes를 담았다
        Long myRoute = newRoute(me, "IN_PROGRESS", mine, true);
        newRoute(newUser(), "IN_PROGRESS", popular, false);
        newRoute(newUser(), "IN_PROGRESS", popular, false);
        newRoute(newUser(), "IN_PROGRESS", noMinutes, false);

        mockMvc.perform(post("/api/v1/routes/{routeId}/arrival", myRoute)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(me).accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.top3.status").value("RANKED"))
            .andExpect(jsonPath("$.data.top3.articles.length()").value(2))
            .andExpect(jsonPath("$.data.top3.articles[0].articleId").value(popular))
            .andExpect(jsonPath("$.data.top3.articles[0].readingMinutes").value(3))
            .andExpect(jsonPath("$.data.top3.articles[0].cartedUserCount").value(2))
            // 읽기시간이 없는 기사도 TOP3에서 빠지거나 TOP3 전체가 사라지지 않고, readingMinutes만 null이다
            .andExpect(jsonPath("$.data.top3.articles[1].articleId").value(noMinutes))
            .andExpect(jsonPath("$.data.top3.articles[1].readingMinutes").doesNotExist())
            .andExpect(jsonPath("$.data.top3.articles[1].cartedUserCount").value(1));
    }

    private Long newUser() {
        return userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
    }

    private Long newArticle(Long feedId, Long categoryId, String readingMinutes) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', ?, ?, 'EASY', ?::numeric, '첫 문장', '둘째 문장', '셋째 문장',
                '한 줄 요약', 'READY', TRUE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            TODAY.atTime(1, 0), TODAY, readingMinutes);
    }

    private Long newArticleWithoutMinutes(Long feedId, Long categoryId) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                process_status, recommendable, visibility)
            VALUES (?, ?, ?, '읽기시간 없는 기사', '테스트일보', ?, ?, 'COLLECTED', FALSE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), TODAY.atTime(1, 0),
            TODAY);
    }

    private Long newRoute(Long userId, String status, Long articleId, boolean read) {
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                started_at)
            VALUES (?, ?, 'SUNNY', 10, ?, 0, ?, ?)
            RETURNING id
            """, Long.class, userId, TODAY, status, TODAY.plusDays(1).atTime(4, 0), TODAY.atTime(8, 0));
        jdbcTemplate.update("""
            INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                cart_order, carted_at, read_at)
            VALUES (?, ?, 1, 'EASY', 2.0, TRUE, 1, ?, ?)
            """, routeId, articleId, TODAY.atTime(7, 50), read ? TODAY.atTime(8, 5) : null);
        return routeId;
    }
}
