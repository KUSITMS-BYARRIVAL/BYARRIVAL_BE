package com.byarrival.domain.scrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관함(LIB-01~05)과 기사 저장 토글(READ-04)을 실제 DB와 보안 필터로 확인한다. 테스트마다 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class ScrapFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private TokenResponse user;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        user = newUser();
    }

    @Test
    void 기사를_저장하고_보관함에서_정렬_필터로_본다() throws Exception {
        Long economy = newCategory(1);
        Long sports = newCategory(2);
        Long first = newArticle(economy, "VISIBLE");
        Long second = newArticle(sports, "VISIBLE");
        Long third = newArticle(economy, "VISIBLE");
        Long hidden = newArticle(economy, "HIDDEN");
        newCategory(3); // 저장 기사가 없는 카테고리는 필터 칩에서 빠진다

        for (Long articleId : List.of(first, second, third)) {
            perform(user, put("/api/v1/scraps/articles/{articleId}", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetId").value(articleId))
                .andExpect(jsonPath("$.data.scrapped").value(true));
        }
        // 다시 저장해도 한 번만 저장된다
        perform(user, put("/api/v1/scraps/articles/{articleId}", first)).andExpect(status().isOk());
        // 숨긴 기사와 없는 기사는 저장할 수 없다
        perform(user, put("/api/v1/scraps/articles/{articleId}", hidden))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCRAP_TARGET_NOT_FOUND"));
        perform(user, put("/api/v1/scraps/articles/{articleId}", 999_999_999L))
            .andExpect(status().isNotFound());

        perform(user, get("/api/v1/scraps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("ARTICLE"))
            .andExpect(jsonPath("$.data.counts.article").value(3))
            .andExpect(jsonPath("$.data.counts.smalltalk").value(0))
            .andExpect(ids("$.data.articles[*].articleId", third.intValue(), second.intValue(), first.intValue()))
            .andExpect(jsonPath("$.data.articles[0].available").value(true))
            .andExpect(jsonPath("$.data.articles[0].title").value("테스트 기사"))
            .andExpect(jsonPath("$.data.articles[0].oneLine").value("한 줄 요약"))
            .andExpect(jsonPath("$.data.articles[0].readingMinutes").value(3))
            .andExpect(jsonPath("$.data.articles[0].sourceUrl").exists())
            // 저장 기사가 있는 카테고리만 화면 순서대로
            .andExpect(ids("$.data.categories[*].categoryId", economy.intValue(), sports.intValue()))
            .andExpect(ids("$.data.categories[*].count", 2, 1));

        perform(user, get("/api/v1/scraps").param("sort", "OLDEST"))
            .andExpect(jsonPath("$.data.articles[0].articleId").value(first));
        perform(user, get("/api/v1/scraps").param("categoryId", String.valueOf(sports)))
            .andExpect(ids("$.data.articles[*].articleId", second.intValue()))
            .andExpect(jsonPath("$.data.categories.length()").value(2));

        // 저장한 뒤 숨겨진 기사는 비활성 카드로 남는다(LIB-05)
        jdbcTemplate.update("UPDATE article SET visibility = 'DELETED' WHERE id = ?", first);
        perform(user, get("/api/v1/scraps").param("sort", "OLDEST"))
            .andExpect(jsonPath("$.data.counts.article").value(3))
            .andExpect(jsonPath("$.data.articles[0].articleId").value(first))
            .andExpect(jsonPath("$.data.articles[0].available").value(false))
            .andExpect(jsonPath("$.data.articles[0].sourceUrl").doesNotExist());
    }

    @Test
    void 보관함에서_지우고_되돌리면_원래_자리로_돌아온다() throws Exception {
        Long category = newCategory(1);
        Long first = newArticle(category, "VISIBLE");
        Long second = newArticle(category, "VISIBLE");
        perform(user, put("/api/v1/scraps/articles/{articleId}", first));
        perform(user, put("/api/v1/scraps/articles/{articleId}", second));
        String library = perform(user, get("/api/v1/scraps").param("sort", "OLDEST"))
            .andReturn().getResponse().getContentAsString();
        Long firstScrapId = ((Number) JsonPath.read(library, "$.data.articles[0].scrapId")).longValue();

        perform(user, delete("/api/v1/scraps/{scrapId}", firstScrapId)).andExpect(status().isOk());
        // 연타해도 그대로
        perform(user, delete("/api/v1/scraps/{scrapId}", firstScrapId)).andExpect(status().isOk());
        perform(user, get("/api/v1/scraps"))
            .andExpect(jsonPath("$.data.counts.article").value(1))
            .andExpect(ids("$.data.articles[*].articleId", second.intValue()));

        perform(user, post("/api/v1/scraps/{scrapId}/restore", firstScrapId)).andExpect(status().isOk());
        perform(user, get("/api/v1/scraps").param("sort", "OLDEST"))
            .andExpect(ids("$.data.articles[*].articleId", first.intValue(), second.intValue()));

        // 다른 사용자의 보관함 항목은 없는 것으로 본다
        TokenResponse other = newUser();
        perform(other, delete("/api/v1/scraps/{scrapId}", firstScrapId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCRAP_NOT_FOUND"));
        perform(other, post("/api/v1/scraps/{scrapId}/restore", firstScrapId))
            .andExpect(status().isNotFound());
    }

    @Test
    void 기사_화면에서_저장을_해제하고_다시_저장하면_최신으로_간다() throws Exception {
        Long category = newCategory(1);
        Long first = newArticle(category, "VISIBLE");
        Long second = newArticle(category, "VISIBLE");
        perform(user, put("/api/v1/scraps/articles/{articleId}", first));
        perform(user, put("/api/v1/scraps/articles/{articleId}", second));

        perform(user, delete("/api/v1/scraps/articles/{articleId}", first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scrapped").value(false));
        // 저장하지 않은 기사를 해제해도 그대로
        perform(user, delete("/api/v1/scraps/articles/{articleId}", first)).andExpect(status().isOk());
        perform(user, get("/api/v1/scraps")).andExpect(jsonPath("$.data.counts.article").value(1));

        jdbcTemplate.update("UPDATE scrap SET created_at = created_at - INTERVAL '1 hour' WHERE target_id = ?", second);
        perform(user, put("/api/v1/scraps/articles/{articleId}", first));
        perform(user, get("/api/v1/scraps"))
            .andExpect(ids("$.data.articles[*].articleId", first.intValue(), second.intValue()));
    }

    // 목록 값 비교. 숫자는 정수로 맞춰 비교한다
    private static ResultMatcher ids(String path, Object... expected) {
        return result -> {
            List<Object> actual = JsonPath.read(result.getResponse().getContentAsString(
                StandardCharsets.UTF_8), path);
            assertThat(actual.stream().map(value -> ((Number) value).longValue()).toList())
                .containsExactly(Arrays.stream(expected).map(value -> ((Number) value).longValue())
                    .toArray(Long[]::new));
        };
    }

    private ResultActions perform(TokenResponse token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken()));
    }

    private TokenResponse newUser() {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        return tokenService.issue(userId);
    }

    private Long newCategory(int displayOrder) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, ?) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8), -100 + displayOrder);
    }

    private Long newArticle(Long categoryId, String visibility) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date, difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', ?, ?, 'EASY', 2.4, '첫 문장', '둘째 문장', '셋째 문장',
                '한 줄 요약', 'READY', TRUE, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), LocalDate.of(2026, 11, 12).atTime(8, 0), LocalDate.of(2026, 11, 13), visibility);
    }
}
