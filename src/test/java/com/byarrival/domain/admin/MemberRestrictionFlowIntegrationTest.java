package com.byarrival.domain.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDateTime;
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
 * 실제 DB와 보안 필터로 어드민의 회원 이용 제한·해제와 제한된 회원의 앱 이용을 확인한다(MEMBER-03). 테스트마다 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MemberRestrictionFlowIntegrationTest {

    private static final String MEMBERS = "/api/admin/members";
    private static final String ME = "/api/v1/users/me";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private Cookie cookie;
    private String adminEmail;
    private String prefix;
    private Long memberId;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", adminEmail,
            PASSWORD_HASH);
        cookie = login(adminEmail);
        prefix = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        memberId = member("target");
    }

    @Test
    void 제한하면_이용_제한_상태와_조치_이력을_돌려준다() throws Exception {
        restrict(memberId, "DAYS_7", "  욕설 신고 누적  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(memberId))
            .andExpect(jsonPath("$.data.status").value("RESTRICTED"))
            .andExpect(jsonPath("$.data.restriction.period").value("DAYS_7"))
            .andExpect(jsonPath("$.data.restriction.reason").value("욕설 신고 누적"))
            .andExpect(jsonPath("$.data.restriction.endsAt").isNotEmpty())
            .andExpect(jsonPath("$.data.restrictionHistory.length()").value(1))
            .andExpect(jsonPath("$.data.restrictionHistory[0].restrictedBy").value(adminEmail))
            .andExpect(jsonPath("$.data.restrictionHistory[0].active").value(true))
            .andExpect(jsonPath("$.data.restrictionHistory[0].releasedAt").doesNotExist());
    }

    @Test
    void 영구_제한은_끝나는_시각이_없다() throws Exception {
        restrict(memberId, "PERMANENT", "운영 정책 위반")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restriction.period").value("PERMANENT"))
            .andExpect(jsonPath("$.data.restriction.endsAt").doesNotExist());
    }

    @Test
    void 기간이나_사유가_없거나_사유가_100자를_넘으면_제한하지_않는다() throws Exception {
        mockMvc.perform(post(MEMBERS + "/" + memberId + "/restriction").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사유\"}"))
            .andExpect(status().isBadRequest());
        restrict(memberId, "DAYS_3", " ").andExpect(status().isBadRequest());
        restrict(memberId, "DAYS_3", "가".repeat(101)).andExpect(status().isBadRequest());
        mockMvc.perform(post(MEMBERS + "/" + memberId + "/restriction").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"DAYS_5\",\"reason\":\"사유\"}"))
            .andExpect(status().isBadRequest());

        detail(memberId).andExpect(jsonPath("$.data.status").value("NORMAL"));
    }

    @Test
    void 이미_제한_중이면_다시_제한하지_않는다() throws Exception {
        restrict(memberId, "DAYS_3", "첫 번째").andExpect(status().isOk());

        restrict(memberId, "DAYS_30", "두 번째")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_MEMBER_ALREADY_RESTRICTED"));
        detail(memberId)
            .andExpect(jsonPath("$.data.restriction.period").value("DAYS_3"))
            .andExpect(jsonPath("$.data.restrictionHistory.length()").value(1));
    }

    @Test
    void 해제하면_정상으로_돌아가고_해제한_운영자를_이력에_남긴다() throws Exception {
        restrict(memberId, "DAYS_30", "오신고 확인 전").andExpect(status().isOk());

        release(memberId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("NORMAL"))
            .andExpect(jsonPath("$.data.restriction").doesNotExist())
            .andExpect(jsonPath("$.data.restrictionHistory[0].releasedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.restrictionHistory[0].releasedBy").value(adminEmail))
            .andExpect(jsonPath("$.data.restrictionHistory[0].active").value(false));
        // 제한 중이 아니어도 성공이다
        release(memberId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NORMAL"));
        // 해제한 뒤에는 다시 제한할 수 있다
        restrict(memberId, "DAYS_3", "재발")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restrictionHistory.length()").value(2))
            .andExpect(jsonPath("$.data.restrictionHistory[0].reason").value("재발"));
    }

    @Test
    void 기간이_지난_제한은_해제하지_않아도_풀린다() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        pastRestriction(memberId, now.minusDays(4), now.minusDays(1));

        detail(memberId)
            .andExpect(jsonPath("$.data.status").value("NORMAL"))
            .andExpect(jsonPath("$.data.restriction").doesNotExist())
            .andExpect(jsonPath("$.data.restrictionHistory[0].active").value(false));
        appGet(memberId, ME).andExpect(status().isOk()).andExpect(jsonPath("$.data.restriction").doesNotExist());
        restrict(memberId, "DAYS_3", "새 제한").andExpect(status().isOk());
    }

    @Test
    void 회원_목록은_상태를_보여_주고_상태로_거른다() throws Exception {
        Long normal = member("normal");
        restrict(memberId, "DAYS_7", "사유").andExpect(status().isOk());

        mockMvc.perform(get(MEMBERS).param("keyword", prefix).cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get(MEMBERS).param("keyword", prefix).param("status", "RESTRICTED").cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.members[0].memberId").value(memberId))
            .andExpect(jsonPath("$.data.members[0].status").value("RESTRICTED"));
        mockMvc.perform(get(MEMBERS).param("keyword", prefix).param("status", "NORMAL").cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.members[0].memberId").value(normal))
            .andExpect(jsonPath("$.data.members[0].status").value("NORMAL"));
    }

    @Test
    void 없거나_탈퇴한_회원은_제한할_수_없다() throws Exception {
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
            LocalDateTime.now(), memberId);

        restrict(memberId, "DAYS_3", "사유")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADMIN_MEMBER_NOT_FOUND"));
        restrict(999_999_999L, "DAYS_3", "사유").andExpect(status().isNotFound());
        release(999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void 제한된_회원은_내_정보에서_사유를_받고_다른_앱_API는_쓸_수_없다() throws Exception {
        restrict(memberId, "DAYS_7", "욕설 신고 누적").andExpect(status().isOk());

        appGet(memberId, ME)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.restriction.reason").value("욕설 신고 누적"))
            .andExpect(jsonPath("$.data.restriction.endsAt").isNotEmpty());
        mockMvc.perform(put(ME + "/notification-permission").header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"permission\":\"GRANTED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_RESTRICTED"));
        appGet(memberId, "/api/v1/notifications")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_RESTRICTED"));
        // 이 기기의 푸시 끊기와 로그아웃은 쓸 수 있다
        mockMvc.perform(delete("/api/v1/devices").header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fcmToken\":\"token-" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void 해제하면_앱을_다시_쓸_수_있다() throws Exception {
        restrict(memberId, "PERMANENT", "사유").andExpect(status().isOk());
        appGet(memberId, "/api/v1/notifications").andExpect(status().isForbidden());

        release(memberId).andExpect(status().isOk());

        appGet(memberId, ME).andExpect(status().isOk()).andExpect(jsonPath("$.data.restriction").doesNotExist());
        appGet(memberId, "/api/v1/notifications").andExpect(status().isOk());
    }

    private ResultActions restrict(Long id, String period, String reason) throws Exception {
        String body = "{\"period\":\"%s\",\"reason\":\"%s\"}".formatted(period, reason);
        return mockMvc.perform(post(MEMBERS + "/" + id + "/restriction").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions release(Long id) throws Exception {
        return mockMvc.perform(delete(MEMBERS + "/" + id + "/restriction").cookie(cookie));
    }

    private ResultActions detail(Long id) throws Exception {
        return mockMvc.perform(get(MEMBERS + "/" + id).cookie(cookie));
    }

    private ResultActions appGet(Long userId, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    private String bearer(Long userId) {
        return "Bearer " + tokenService.issue(userId).accessToken();
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

    private Long member(String suffix) {
        LocalDateTime joinedAt = LocalDateTime.of(2030, 1, 1, 9, 0);
        return jdbcTemplate.queryForObject("""
            INSERT INTO users (provider, provider_user_id, nickname, travel_type, created_at, updated_at)
            VALUES ('KAKAO', ?, ?, 'WORKER', ?, ?)
            RETURNING id
            """, Long.class, UUID.randomUUID().toString(), prefix + suffix, joinedAt, joinedAt);
    }

    private void pastRestriction(Long userId, LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbcTemplate.update("""
            INSERT INTO user_restriction (user_id, admin_id, period, reason, starts_at, ends_at)
            VALUES (?, (SELECT id FROM admin_account WHERE email = ?), 'DAYS_3', '지난 제한', ?, ?)
            """, userId, adminEmail, startsAt, endsAt);
    }
}
