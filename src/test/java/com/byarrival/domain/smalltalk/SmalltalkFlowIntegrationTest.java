package com.byarrival.domain.smalltalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 스몰토크 목록·상세(PICK-01, PICK-02)와 스크랩·보관함 탭(PICK-03, LIB-01)을 실제 DB로 확인한다.
 * 서비스일은 다른 테스트와 겹치지 않는 2032-03-02로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, SmalltalkFlowIntegrationTest.FixedClockConfig.class})
class SmalltalkFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2032, 3, 2);
    private static final LocalDateTime NOW = TODAY.atTime(9, 0);

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
    private TokenResponse user;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        user = tokenService.issue(
            userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId());
        jdbcTemplate.update("DELETE FROM smalltalk WHERE service_date = ?", TODAY);
    }

    @Test
    void 오늘의_스몰토크를_보고_저장하면_보관함_스몰토크_탭에_나온다() throws Exception {
        Long first = newCategory(-50);
        Long second = newCategory(-60);
        Long economyTalk = newSmalltalk(newArticle(first, "경제 기사", "VISIBLE"), TODAY, "경제 얘기 들으셨어요?", "VISIBLE");
        Long sportsTalk = newSmalltalk(newArticle(second, "스포츠 기사", "VISIBLE"), TODAY, "어제 경기 보셨어요?", "VISIBLE");
        Long hiddenArticleTalk = newSmalltalk(newArticle(first, "숨긴 기사", "HIDDEN"), TODAY, "숨긴 기사 얘기예요.",
            "VISIBLE");
        Long hiddenTalk = newSmalltalk(newArticle(first, "다른 기사", "VISIBLE"), TODAY, "숨긴 스몰토크예요.", "HIDDEN");
        Long yesterdayTalk = newSmalltalk(newArticle(first, "어제 기사", "VISIBLE"), TODAY.minusDays(1), "어제 얘기예요.",
            "VISIBLE");

        String list = perform(get("/api/v1/smalltalks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceDate").value(TODAY.toString()))
            .andExpect(jsonPath("$.data.smalltalks[0].hook").value("어제 경기 보셨어요?"))
            .andExpect(jsonPath("$.data.smalltalks[0].articleTitle").value("스포츠 기사"))
            .andExpect(jsonPath("$.data.smalltalks[0].sourceUrl").exists())
            .andExpect(jsonPath("$.data.smalltalks[0].scrapped").value(false))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 카테고리 화면 순서대로, 숨긴 스몰토크·숨긴 기사의 스몰토크·어제 스몰토크는 빠진다
        List<Integer> ids = JsonPath.read(list, "$.data.smalltalks[*].smalltalkId");
        assertThat(ids).containsExactly(sportsTalk.intValue(), economyTalk.intValue());

        perform(get("/api/v1/smalltalks/{id}", economyTalk))
            .andExpect(jsonPath("$.data.hook").value("경제 얘기 들으셨어요?"))
            .andExpect(jsonPath("$.data.categoryId").value(first))
            .andExpect(jsonPath("$.data.scrapped").value(false));
        perform(get("/api/v1/smalltalks/{id}", hiddenArticleTalk))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SMALLTALK_NOT_FOUND"));
        perform(get("/api/v1/smalltalks/{id}", hiddenTalk)).andExpect(status().isNotFound());
        // 지난 스몰토크도 상세는 열린다(보관함에서 진입)
        perform(get("/api/v1/smalltalks/{id}", yesterdayTalk)).andExpect(status().isOk());

        perform(put("/api/v1/scraps/smalltalks/{id}", economyTalk))
            .andExpect(jsonPath("$.data.scrapped").value(true));
        perform(put("/api/v1/scraps/smalltalks/{id}", yesterdayTalk)).andExpect(status().isOk());
        perform(put("/api/v1/scraps/smalltalks/{id}", hiddenTalk))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCRAP_TARGET_NOT_FOUND"));
        perform(get("/api/v1/smalltalks/{id}", economyTalk)).andExpect(jsonPath("$.data.scrapped").value(true));

        String library = perform(get("/api/v1/scraps").param("type", "SMALLTALK"))
            .andExpect(jsonPath("$.data.type").value("SMALLTALK"))
            .andExpect(jsonPath("$.data.counts.smalltalk").value(2))
            .andExpect(jsonPath("$.data.counts.article").value(0))
            .andExpect(jsonPath("$.data.articles.length()").value(0))
            .andExpect(jsonPath("$.data.smalltalks[0].available").value(true))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Integer> saved = JsonPath.read(library, "$.data.smalltalks[*].smalltalkId");
        assertThat(saved).containsExactlyInAnyOrder(economyTalk.intValue(), yesterdayTalk.intValue());
        // 스몰토크 탭도 카테고리 칩과 필터를 쓴다
        List<Integer> chips = JsonPath.read(library, "$.data.categories[*].categoryId");
        assertThat(chips).containsExactly(first.intValue());
        perform(get("/api/v1/scraps").param("type", "SMALLTALK").param("categoryId", String.valueOf(second)))
            .andExpect(jsonPath("$.data.smalltalks.length()").value(0));

        perform(delete("/api/v1/scraps/smalltalks/{id}", economyTalk))
            .andExpect(jsonPath("$.data.scrapped").value(false));
        perform(get("/api/v1/scraps").param("type", "SMALLTALK"))
            .andExpect(jsonPath("$.data.counts.smalltalk").value(1));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken()));
    }

    private Long newCategory(int displayOrder) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, ?) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8), displayOrder);
    }

    private Long newArticle(Long categoryId, String title, String visibility) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', TRUE, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            TODAY.atTime(1, 0), TODAY, visibility);
    }

    private Long newSmalltalk(Long articleId, LocalDate serviceDate, String hook, String visibility) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO smalltalk (article_id, service_date, hook, visibility) VALUES (?, ?, ?, ?) RETURNING id
            """, Long.class, articleId, serviceDate, hook, visibility);
    }
}
