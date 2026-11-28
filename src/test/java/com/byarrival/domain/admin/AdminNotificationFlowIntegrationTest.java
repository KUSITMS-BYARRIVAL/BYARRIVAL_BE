package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.notification.service.BroadcastDispatcher;
import com.byarrival.support.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 어드민 알림 작성·예약·발송·테스트 발송·발송 이력(NOTI-01·02).
 * 발송은 커밋 뒤 별도 스레드에서 이어지므로 롤백하지 않고, 만든 회원은 테스트가 끝나면 탈퇴 처리해 다른 테스트의 대상에서 뺀다.
 * 지금 시각은 낮 10:00으로 고정한다.
 */
// 로컬에 FCM을 켜 둬도 실제로 보내지 않도록 로그 발송기를 쓴다
@SpringBootTest(properties = {"notification.broadcast.test-user-ids=" + AdminNotificationFlowIntegrationTest.TEST_USER_ID,
    "notification.push.fcm-enabled=false"})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, AdminNotificationFlowIntegrationTest.FixedClockConfig.class})
class AdminNotificationFlowIntegrationTest {

    static final long TEST_USER_ID = 987_654_321L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDate.now(KST).atTime(10, 0);
    private static final String NOTIFICATIONS = "/api/admin/notifications";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BroadcastDispatcher dispatcher;

    private Cookie cookie;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        String email = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", email, PASSWORD_HASH);
        cookie = login(email);
    }

    @AfterEach
    void tearDown() {
        created.forEach(id -> jdbcTemplate.update(
            "UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ? AND status = 'ACTIVE'", NOW, id));
    }

    @Test
    void 예상_인원은_수신_설정을_끈_사람과_미동의자를_빼고_야간에는_야간_동의자만_센다() throws Exception {
        long dayBefore = estimate("EVENT", "WORKER", null);
        long nightBefore = estimate("EVENT", "WORKER", NOW.withHour(22));
        member("WORKER", true, false);
        member("WORKER", true, true);
        member("WORKER", false, false);
        Long settingOff = member("WORKER", true, false);
        jdbcTemplate.update("INSERT INTO user_notification_setting (user_id, category, enabled) VALUES (?, 'ACTIVITY', FALSE)",
            settingOff);
        Long denied = member("WORKER", true, false);
        jdbcTemplate.update("UPDATE users SET notification_permission = 'DENIED' WHERE id = ?", denied);
        Long restricted = member("WORKER", true, false);
        jdbcTemplate.update("""
            INSERT INTO user_restriction (user_id, admin_id, period, reason, starts_at)
            VALUES (?, (SELECT max(id) FROM admin_account), 'PERMANENT', '사유', ?)
            """, restricted, NOW.minusDays(1));

        assertThat(estimate("EVENT", "WORKER", null)).isEqualTo(dayBefore + 2);
        assertThat(estimate("EVENT", "WORKER", NOW.withHour(22))).isEqualTo(nightBefore + 1);
        mockMvc.perform(get(NOTIFICATIONS + "/estimate").param("kind", "EVENT").param("target", "WORKER")
                .param("scheduledAt", NOW.withHour(22).toString()).cookie(cookie))
            .andExpect(jsonPath("$.data.night").value(true));
    }

    @Test
    void 이벤트를_즉시_보내면_동의자에게만_광고_표시를_붙여_보내고_결과를_센다() throws Exception {
        Long withDevice = member("STUDENT", true, false);
        jdbcTemplate.update("INSERT INTO device_token (user_id, token, platform) VALUES (?, ?, 'ANDROID')", withDevice,
            "token-" + UUID.randomUUID());
        Long noDevice = member("STUDENT", true, false);
        Long notAgreed = member("STUDENT", false, false);

        Long id = create("EVENT", "STUDENT", "겨울 이벤트", "TODAY_SMALLTALK", null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayTitle").value("(광고) 겨울 이벤트"))
            .andReturn().getResponse().getContentAsString().transform(body -> idOf(body));
        dispatcher.dispatchDue();
        waitUntilCompleted(id);

        assertThat(titlesOf(withDevice)).containsExactly("(광고) 겨울 이벤트");
        assertThat(titlesOf(noDevice)).containsExactly("(광고) 겨울 이벤트");
        assertThat(titlesOf(notAgreed)).isEmpty();
        String params = jdbcTemplate.queryForObject(
            "SELECT params FROM notification WHERE user_id = ? AND type = 'EVENT'", String.class, withDevice);
        assertThat(params).contains("\"screen\":\"TODAY_SMALLTALK\"").contains("\"broadcastId\":\"" + id + "\"");
        mockMvc.perform(get(NOTIFICATIONS + "/" + id).cookie(cookie))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.recipientCount").value(2))
            .andExpect(jsonPath("$.data.successCount").value(1))
            .andExpect(jsonPath("$.data.failureCount").value(1))
            .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void 예약은_시각이_되기_전까지_보내지_않고_수정과_취소를_할_수_있다() throws Exception {
        Long member = member("ETC", true, false);
        LocalDateTime noon = NOW.withHour(12).withSecond(30);

        Long id = create("EVENT", "ETC", "예약 알림", "HOME", null, noon)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.scheduledAt").value(NOW.withHour(12).toString() + ":00"))
            .andExpect(jsonPath("$.data.editable").value(true))
            .andReturn().getResponse().getContentAsString().transform(body -> idOf(body));
        dispatcher.dispatchDue();
        assertThat(titlesOf(member)).isEmpty();

        mockMvc.perform(put(NOTIFICATIONS + "/" + id).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content(body("EVENT", "ETC", "고친 예약", "HOME", null, NOW.withHour(13))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("고친 예약"));
        mockMvc.perform(post(NOTIFICATIONS + "/" + id + "/cancel").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(post(NOTIFICATIONS + "/" + id + "/cancel").cookie(cookie))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BROADCAST_NOT_EDITABLE"));
    }

    @Test
    void 받을_사람이_없거나_입력이_맞지_않으면_보내지_않는다() throws Exception {
        member("WORKER", true, false);

        // 야간(22시)에는 야간 동의자가 없어 0명이다
        create("EVENT", "WORKER", "야간 광고", "HOME", null, NOW.withHour(22).plusDays(0))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BROADCAST_NO_RECIPIENT"));
        create("NOTICE", "ALL", "지난 예약", "HOME", null, NOW.minusMinutes(1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BROADCAST_PAST_SCHEDULE"));
        create("NOTICE", "ALL", "공지", "NOTICE", null, NOW.plusHours(1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BROADCAST_INVALID_LINK"));
        create("NOTICE", "ALL", "공지", "HOME", "https://byarrival.example.com/notice/1", NOW.plusHours(1))
            .andExpect(status().isBadRequest());
        create("NOTICE", "ALL", "가".repeat(21), "HOME", null, NOW.plusHours(1)).andExpect(status().isBadRequest());
    }

    @Test
    void 발송_이력은_수동_발송은_건별로_자동_알림은_일자와_유형별_건수로_보여_준다() throws Exception {
        Long member = member("ETC", true, false);
        Long id = create("EVENT", "ETC", "이력 알림", "HOME", null, null)
            .andReturn().getResponse().getContentAsString().transform(body -> idOf(body));
        dispatcher.dispatchDue();
        waitUntilCompleted(id);
        LocalDate day = LocalDate.of(2026, 1, 15);
        // 자동 알림은 회원마다 하루 한 번이라 회원을 나눠 남긴다
        for (String status : List.of("SENT", "SENT", "FAILED", "NO_DEVICE", "SKIPPED")) {
            jdbcTemplate.update("""
                INSERT INTO notification (user_id, type, title, body, service_date, push_status, created_at)
                VALUES (?, 'SMALLTALK', '제목', '내용', ?, ?, ?)
                """, member("ETC", false, false), day, status, NOW);
        }

        mockMvc.perform(get(NOTIFICATIONS + "/history").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.to").value(LocalDate.from(NOW).toString()))
            .andExpect(jsonPath("$.data.manual[?(@.broadcastId == %d)].title".formatted(id)).value("이력 알림"));
        String history = mockMvc.perform(get(NOTIFICATIONS + "/history").param("from", day.toString())
                .param("to", day.toString()).cookie(cookie))
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(history, "$.data.auto[?(@.type == 'SMALLTALK')]");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("successCount")).isEqualTo(2);
            assertThat(row.get("failureCount")).isEqualTo(2);
        });
        mockMvc.perform(get(NOTIFICATIONS + "/history").param("from", day.toString())
                .param("to", day.plusDays(100).toString()).cookie(cookie))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 테스트_발송은_설정한_회원_기기로_바로_보낸다() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO users (id, provider, provider_user_id) VALUES (?, 'KAKAO', ?) ON CONFLICT (id) DO NOTHING
            """, TEST_USER_ID, "test-push-" + UUID.randomUUID());
        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM device_token WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("INSERT INTO device_token (user_id, token, platform) VALUES (?, ?, 'IOS')", TEST_USER_ID,
            "token-" + UUID.randomUUID());

        mockMvc.perform(post(NOTIFICATIONS + "/test").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind":"EVENT","title":"테스트","body":"테스트 본문","screen":"NOTICE",
                     "url":"https://byarrival.example.com/notice/1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[0].userId").value(TEST_USER_ID))
            .andExpect(jsonPath("$.data.results[0].deliveredDevices").value(1));

        Map<String, Object> saved = jdbcTemplate.queryForMap(
            "SELECT title, params FROM notification WHERE user_id = ? AND type = 'TEST' ORDER BY id DESC LIMIT 1",
            TEST_USER_ID);
        assertThat(saved.get("title")).isEqualTo("(광고) 테스트");
        assertThat((String) saved.get("params")).contains("\"url\":\"https://byarrival.example.com/notice/1\"");
    }

    private long estimate(String kind, String target, LocalDateTime scheduledAt) throws Exception {
        var request = get(NOTIFICATIONS + "/estimate").param("kind", kind).param("target", target).cookie(cookie);
        if (scheduledAt != null) {
            request.param("scheduledAt", scheduledAt.toString());
        }
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.count")).longValue();
    }

    private ResultActions create(String kind, String target, String title, String screen, String url,
        LocalDateTime scheduledAt) throws Exception {
        return mockMvc.perform(post(NOTIFICATIONS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(body(kind, target, title, screen, url, scheduledAt)));
    }

    private static String body(String kind, String target, String title, String screen, String url,
        LocalDateTime scheduledAt) {
        return "{\"kind\":\"%s\",\"target\":\"%s\",\"title\":\"%s\",\"body\":\"본문입니다\",\"screen\":\"%s\",\"url\":%s,\"scheduledAt\":%s}"
            .formatted(kind, target, title, screen, url == null ? "null" : "\"" + url + "\"",
                scheduledAt == null ? "null" : "\"" + scheduledAt + "\"");
    }

    private static Long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.data.broadcastId")).longValue();
    }

    // 즉시 발송은 별도 스레드에서도 시작하므로 끝날 때까지 기다린다
    private void waitUntilCompleted(Long id) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String status = jdbcTemplate.queryForObject("SELECT status FROM notification_broadcast WHERE id = ?",
                String.class, id);
            Integer pending = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM notification WHERE params LIKE ? AND push_status = 'PENDING'
                """, Integer.class, "%\"broadcastId\":\"" + id + "\"%");
            if ("COMPLETED".equals(status) && pending == 0) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("발송이 끝나지 않았습니다: broadcastId=" + id);
    }

    private List<String> titlesOf(Long userId) {
        return jdbcTemplate.queryForList("SELECT title FROM notification WHERE user_id = ? AND type = 'EVENT'",
            String.class, userId);
    }

    private Long member(String travelType, boolean marketing, boolean night) {
        Long id = jdbcTemplate.queryForObject("""
            INSERT INTO users (provider, provider_user_id, travel_type, onboarding_status, onboarded_at)
            VALUES ('KAKAO', ?, ?, 'COMPLETED', ?)
            RETURNING id
            """, Long.class, UUID.randomUUID().toString(), travelType, NOW.minusDays(10));
        created.add(id);
        if (marketing) {
            agree(id, "MARKETING");
        }
        if (night) {
            agree(id, "MARKETING_NIGHT");
        }
        return id;
    }

    private void agree(Long userId, String type) {
        jdbcTemplate.update("""
            INSERT INTO user_terms_agreement (user_id, terms_id, agreed, agreed_at)
            VALUES (?, (SELECT max(id) FROM terms WHERE terms_type = ?), TRUE, ?)
            """, userId, type, NOW.minusDays(1));
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
}
