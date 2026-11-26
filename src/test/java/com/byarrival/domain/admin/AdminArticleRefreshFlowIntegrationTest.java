package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.batch.service.DailyBatchService;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB로 어드민 새로고침의 삭제 범위·실행 중 거절·상태 조회를 확인한다.
 * 배치 실행부(수집·AI 호출)는 비용이 들므로 가짜로 바꾸고, 호출됐는지만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, AdminArticleRefreshFlowIntegrationTest.FixedClockConfig.class})
@Transactional
class AdminArticleRefreshFlowIntegrationTest {

    private static final String REFRESH = "/api/admin/articles/refresh";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private static final LocalDate TODAY = LocalDate.of(2031, 4, 9);
    private static final LocalDate NEXT = TODAY.plusDays(1);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        // 오후 2시. 다음 서비스일은 아직 노출 전이다
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(TODAY.atTime(14, 0).atZone(kst).toInstant(), kst);
        }
    }

    @MockitoBean
    private DailyBatchService dailyBatchService;

    @MockitoBean
    private BatchAlertNotifier batchAlertNotifier;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Cookie cookie;
    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", email, PASSWORD_HASH);
        String setCookie = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        cookie = new Cookie(AdminCookieFactory.COOKIE_NAME, pair.substring(pair.indexOf('=') + 1));
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트 언론', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    void 새로고침하면_다음_서비스일_기사와_딸린_데이터를_지우고_배치를_다시_실행한다() throws Exception {
        Long representative = article("대표 기사", NEXT, true);
        Long duplicate = article("같은 사건 기사", NEXT, false);
        jdbcTemplate.update("UPDATE article SET duplicate_of_article_id = ? WHERE id = ?", representative, duplicate);
        quiz(representative);
        jdbcTemplate.update("INSERT INTO smalltalk (article_id, service_date, hook) VALUES (?, ?, '운영자 한마디')",
            representative, NEXT);
        Long todayArticle = article("오늘 기사", TODAY, true);
        quiz(todayArticle);
        Long completed = batchRun(NEXT, "COMPLETED");

        mockMvc.perform(post(REFRESH).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.serviceDate").value(NEXT.toString()))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.finishedAt").doesNotExist());

        assertThat(count("SELECT COUNT(*) FROM article WHERE service_date = ? AND feed_id = ?", NEXT, feedId))
            .isZero();
        assertThat(count("SELECT COUNT(*) FROM quiz WHERE article_id = ?", representative)).isZero();
        assertThat(count("SELECT COUNT(*) FROM smalltalk WHERE article_id = ?", representative)).isZero();
        assertThat(count("SELECT COUNT(*) FROM article WHERE id = ?", todayArticle)).isOne();
        assertThat(count("SELECT COUNT(*) FROM quiz WHERE article_id = ?", todayArticle)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM batch_run WHERE id = ?", String.class, completed))
            .isEqualTo("REPLACED");
        Long running = jdbcTemplate.queryForObject(
            "SELECT id FROM batch_run WHERE service_date = ? AND status = 'RUNNING'", Long.class, NEXT);
        verify(dailyBatchService, timeout(2000)).process(running, NEXT);
        verify(batchAlertNotifier).send(argThat(content -> content.contains("어드민 새로고침 시작")
            && content.contains("서비스일 " + NEXT) && content.contains("기존 기사 2건")));
    }

    @Test
    void 배치가_없던_서비스일도_새로고침할_수_있다() throws Exception {
        mockMvc.perform(post(REFRESH).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RUNNING"));

        verify(dailyBatchService, timeout(2000)).process(anyLong(), eq(NEXT));
    }

    @Test
    void 배치가_실행_중이면_409이고_기사를_지우지_않는다() throws Exception {
        Long kept = article("남는 기사", NEXT, true);
        batchRun(NEXT, "RUNNING");

        mockMvc.perform(post(REFRESH).cookie(cookie))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("BATCH_RUNNING"));

        assertThat(count("SELECT COUNT(*) FROM article WHERE id = ?", kept)).isOne();
        verify(dailyBatchService, after(300).never()).process(anyLong(), eq(NEXT));
        verify(batchAlertNotifier, never()).send(argThat(content -> content.contains("어드민 새로고침 시작")));
    }

    @Test
    void 다음_서비스일_배치가_없으면_NONE이다() throws Exception {
        mockMvc.perform(get(REFRESH).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceDate").value(NEXT.toString()))
            .andExpect(jsonPath("$.data.status").value("NONE"))
            .andExpect(jsonPath("$.data.startedAt").doesNotExist());

    }

    @Test
    void 실행_중이면_RUNNING이고_추천_기사_수는_없다() throws Exception {
        batchRun(NEXT, "FAILED");
        batchRun(NEXT, "RUNNING");

        mockMvc.perform(get(REFRESH).cookie(cookie))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.recommendableCount").doesNotExist());
    }

    @Test
    void 완료된_실행은_추천_기사_수를_함께_보여_준다() throws Exception {
        batchRun(NEXT, "FAILED");
        batchRun(NEXT, "COMPLETED");
        article("추천 기사", NEXT, true);
        article("같은 사건이라 빠진 기사", NEXT, false);

        mockMvc.perform(get(REFRESH).cookie(cookie))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.finishedAt").exists())
            .andExpect(jsonPath("$.data.recommendableCount").value(1));
    }

    @Test
    void 로그인하지_않으면_새로고침할_수_없다() throws Exception {
        mockMvc.perform(post(REFRESH)).andExpect(status().isUnauthorized());

        verify(dailyBatchService, after(300).never()).process(anyLong(), eq(NEXT));
    }

    private Long article(String title, LocalDate serviceDate, boolean recommendable) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, guid, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line,
                process_status, recommendable)
            VALUES (?, ?, ?, ?, ?, '테스트 언론', ?, ?, 'EASY', 1.5, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            UUID.randomUUID().toString(), title, serviceDate.atTime(3, 0), serviceDate, recommendable);
    }

    private void quiz(Long articleId) {
        jdbcTemplate.update("""
            INSERT INTO quiz (article_id, question, choices, answer_seq, explanation)
            VALUES (?, '문제', '[{"seq":1,"content":"정답"},{"seq":2,"content":"오답"},{"seq":3,"content":"오답2"}]'::jsonb,
                1, '해설')
            """, articleId);
    }

    private Long batchRun(LocalDate serviceDate, String status) {
        LocalDateTime startedAt = serviceDate.minusDays(1).atTime(23, 45);
        LocalDateTime finishedAt = status.equals("RUNNING") ? null : startedAt.plusMinutes(8);
        return jdbcTemplate.queryForObject(
            "INSERT INTO batch_run (service_date, status, started_at, finished_at) VALUES (?, ?, ?, ?) RETURNING id",
            Long.class, serviceDate, status, startedAt, finishedAt);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
