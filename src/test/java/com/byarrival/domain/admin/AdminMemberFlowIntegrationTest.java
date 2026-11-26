package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
 * 실제 DB와 보안 필터로 어드민 회원 목록·상세·열람 기록을 확인한다. 테스트마다 롤백하고 닉네임 접두사로 데이터를 구분한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminMemberFlowIntegrationTest {

    private static final String MEMBERS = "/api/admin/members";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Cookie cookie;
    private Long adminId;
    private String prefix;

    @BeforeEach
    void setUp() throws Exception {
        String email = "ops-" + UUID.randomUUID() + "@example.com";
        adminId = jdbcTemplate.queryForObject(
            "INSERT INTO admin_account (email, password_hash) VALUES (?, ?) RETURNING id", Long.class, email,
            PASSWORD_HASH);
        cookie = login(email);
        prefix = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void 목록은_최신_가입순이고_탈퇴_회원을_뺀다() throws Exception {
        Long older = member("first", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));
        Long newer = member("second", "APPLE", "STUDENT", LocalDateTime.of(2030, 1, 2, 9, 0));
        Long withdrawn = member("gone", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 3, 9, 0));
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
            LocalDateTime.now(), withdrawn);

        search("keyword", prefix)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.members[0].memberId").value(newer))
            .andExpect(jsonPath("$.data.members[0].provider").value("APPLE"))
            .andExpect(jsonPath("$.data.members[0].travelType").value("STUDENT"))
            .andExpect(jsonPath("$.data.members[1].memberId").value(older))
            .andExpect(jsonPath("$.data.members[1].providerUserId").doesNotExist());
    }

    @Test
    void 닉네임과_회원번호로_검색하고_유형과_가입_수단으로_거른다() throws Exception {
        Long kakao = member("kakao", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));
        member("apple", "APPLE", "STUDENT", LocalDateTime.of(2030, 1, 2, 9, 0));

        search("keyword", prefix + "kak").andExpect(jsonPath("$.data.totalElements").value(1));
        search("keyword", "#" + kakao).andExpect(jsonPath("$.data.members[0].memberId").value(kakao));
        mockMvc.perform(get(MEMBERS).param("keyword", prefix).param("provider", "APPLE").cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.members[0].provider").value("APPLE"));
        mockMvc.perform(get(MEMBERS).param("keyword", prefix).param("travelType", "ETC").cookie(cookie))
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 검색어의_퍼센트는_글자_그대로_찾는다() throws Exception {
        member("plain", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));

        search("keyword", prefix + "%").andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 한_페이지는_20명이고_다음_페이지를_알려_준다() throws Exception {
        for (int i = 0; i < 21; i++) {
            member("u" + i, "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0).plusMinutes(i));
        }

        search("keyword", prefix)
            .andExpect(jsonPath("$.data.members.length()").value(20))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get(MEMBERS).param("keyword", prefix).param("page", "2").cookie(cookie))
            .andExpect(jsonPath("$.data.members.length()").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false));
        search("page", "0").andExpect(status().isBadRequest());
    }

    @Test
    void 목록은_직군과_근무_지역을_보여_준다() throws Exception {
        Long member = member("job", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));
        profile(member);
        member("empty", "KAKAO", "WORKER", LocalDateTime.of(2029, 12, 31, 9, 0));

        search("keyword", prefix)
            .andExpect(jsonPath("$.data.members[0].memberId").value(member))
            .andExpect(jsonPath("$.data.members[0].jobGroup.code").value("IT_DATA"))
            .andExpect(jsonPath("$.data.members[0].jobGroup.name").value("IT개발·데이터"))
            .andExpect(jsonPath("$.data.members[0].region.sidoName").value("서울"))
            .andExpect(jsonPath("$.data.members[0].region.sigunguName").value("강남구"))
            .andExpect(jsonPath("$.data.members[1].jobGroup").doesNotExist())
            .andExpect(jsonPath("$.data.members[1].region").doesNotExist());
    }

    @Test
    void 상세는_화면의_항목을_돌려주고_열람_기록을_남긴다() throws Exception {
        LocalDate today = ServiceDay.today(clock);
        LocalDate last = previousWeekday(today);
        LocalDate beforeLast = previousWeekday(last);
        Long member = member("detail", "APPLE", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));
        profile(member);
        jdbcTemplate.update("UPDATE users SET default_commute_minutes = 30, mon_start_time = '08:00', mon_minutes = 30 "
            + "WHERE id = ?", member);
        completedRoute(member, beforeLast);
        completedRoute(member, last);

        mockMvc.perform(get(MEMBERS + "/" + member).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(member))
            .andExpect(jsonPath("$.data.provider").value("APPLE"))
            .andExpect(jsonPath("$.data.lastRideAt").value(last.atTime(8, 0).toString() + ":00"))
            .andExpect(jsonPath("$.data.defaultCommuteMinutes").value(30))
            .andExpect(jsonPath("$.data.weeklyCommute[0].dayOfWeek").value("MONDAY"))
            .andExpect(jsonPath("$.data.jobGroup.code").value("IT_DATA"))
            .andExpect(jsonPath("$.data.region.sigunguName").value("강남구"))
            .andExpect(jsonPath("$.data.streak.current").value(2))
            .andExpect(jsonPath("$.data.streak.longest").value(2))
            .andExpect(jsonPath("$.data.thisMonth").doesNotExist())
            .andExpect(jsonPath("$.data.notification").doesNotExist())
            .andExpect(jsonPath("$.data.providerUserId").doesNotExist());

        assertThat(accessLogCount(member)).isEqualTo(1);
        mockMvc.perform(get(MEMBERS + "/" + member).cookie(cookie)).andExpect(status().isOk());
        assertThat(accessLogCount(member)).isEqualTo(2);
    }

    @Test
    void 출발한_적이_없으면_최근_탑승은_없다() throws Exception {
        Long member = member("noride", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));

        mockMvc.perform(get(MEMBERS + "/" + member).cookie(cookie))
            .andExpect(jsonPath("$.data.lastRideAt").doesNotExist())
            .andExpect(jsonPath("$.data.streak.current").value(0));
    }

    @Test
    void 없거나_탈퇴한_회원은_404이고_열람_기록을_남기지_않는다() throws Exception {
        Long withdrawn = member("gone", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
            LocalDateTime.now(), withdrawn);

        mockMvc.perform(get(MEMBERS + "/" + withdrawn).cookie(cookie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ADMIN_MEMBER_NOT_FOUND"));
        mockMvc.perform(get(MEMBERS + "/999999999").cookie(cookie)).andExpect(status().isNotFound());

        assertThat(accessLogCount(withdrawn)).isZero();
    }

    @Test
    void 에디터는_회원_API에_접근할_수_없고_기사_API는_쓸_수_있다() throws Exception {
        String email = "editor-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash, role) VALUES (?, ?, 'EDITOR')", email,
            PASSWORD_HASH);
        Cookie editor = login(email);
        Long member = member("private", "KAKAO", "WORKER", LocalDateTime.of(2030, 1, 1, 9, 0));

        mockMvc.perform(get(MEMBERS).cookie(editor))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(MEMBERS + "/" + member).cookie(editor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/articles").cookie(editor)).andExpect(status().isOk());
        assertThat(accessLogCount(member)).isZero();
    }

    @Test
    void 쿠키_없이는_회원_API를_쓸_수_없다() throws Exception {
        mockMvc.perform(get(MEMBERS)).andExpect(status().isUnauthorized());
    }

    private ResultActions search(String name, String value) throws Exception {
        return mockMvc.perform(get(MEMBERS).param(name, value).cookie(cookie));
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

    private Long member(String suffix, String provider, String travelType, LocalDateTime joinedAt) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO users (provider, provider_user_id, nickname, travel_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class, provider, UUID.randomUUID().toString(), prefix + suffix, travelType, joinedAt, joinedAt);
    }

    // 직군 IT_DATA, 근무 지역 101(서울 강남구)
    private void profile(Long userId) {
        jdbcTemplate.update("UPDATE users SET region_sigungu_id = 101 WHERE id = ?", userId);
        jdbcTemplate.update("INSERT INTO user_job_group (user_id, job_group, sort_order) VALUES (?, 'IT_DATA', 1)",
            userId);
    }

    private void completedRoute(Long userId, LocalDate serviceDate) {
        LocalDateTime startedAt = serviceDate.atTime(8, 0);
        jdbcTemplate.update("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                started_at, completed_at)
            VALUES (?, ?, 'SUNNY', 10, 'COMPLETED', 0, ?, ?, ?)
            """, userId, serviceDate, serviceDate.plusDays(1).atTime(4, 0), startedAt, startedAt.plusMinutes(10));
    }

    private int accessLogCount(Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM admin_member_access_log WHERE user_id = ? AND admin_id = ?", Integer.class, userId,
            adminId);
    }

    private static LocalDate previousWeekday(LocalDate date) {
        LocalDate day = date.minusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day;
    }
}
