package com.byarrival.domain.home;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 DB·Redis와 보안 필터로 홈 조회, 오늘 접속자 수(회원 단위 중복 제외), 출근길 공감대 칩을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class HomeFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 홈을_조회하면_영역별_데이터와_서비스일을_돌려준다() throws Exception {
        TokenResponse tokens = newUser();

        home(tokens)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceDate").exists())
            .andExpect(jsonPath("$.data.todayRoute.state").value("NONE"))
            .andExpect(jsonPath("$.data.streakDays").value(0))
            .andExpect(jsonPath("$.data.smalltalks").isArray())
            .andExpect(jsonPath("$.data.todayVisitorCount").isNumber())
            .andExpect(jsonPath("$.data.popular").isArray());
    }

    @Test
    void 오늘_접속자_수는_회원_단위로_한_번만_센다() throws Exception {
        TokenResponse first = newUser();
        long before = visitorCount(home(first));
        // 같은 회원이 다른 API를 불러도 늘지 않는다
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(first)));
        long again = visitorCount(home(first));

        long afterSecond = visitorCount(home(newUser()));

        assertThat(again).isEqualTo(before);
        assertThat(afterSecond).isEqualTo(before + 1);
    }

    @Test
    void 출근길_공감대는_고른_직군_칩을_고른_순서대로_보여_주고_마지막에_전체_칩을_보여_준다() throws Exception {
        TokenResponse tokens = newUser();
        Long userId = userIdOf(tokens);
        jdbcTemplate.update("INSERT INTO user_job_group (user_id, job_group, sort_order) VALUES (?, 'DESIGN', 1)",
            userId);
        jdbcTemplate.update("INSERT INTO user_job_group (user_id, job_group, sort_order) VALUES (?, 'IT_DATA', 2)",
            userId);

        home(tokens)
            .andExpect(jsonPath("$.data.popular.length()").value(3))
            .andExpect(jsonPath("$.data.popular[0].jobGroup").value("DESIGN"))
            .andExpect(jsonPath("$.data.popular[1].jobGroup").value("IT_DATA"))
            .andExpect(jsonPath("$.data.popular[2].jobGroup").doesNotExist())
            .andExpect(jsonPath("$.data.popular[2].status").exists());
    }

    @Test
    void 전체보기는_직군을_고르면_그_직군_칩_빼면_전체_칩이다() throws Exception {
        TokenResponse tokens = newUser();

        mockMvc.perform(get("/api/v1/home/popular").param("jobGroup", "IT_DATA")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobGroup").value("IT_DATA"))
            .andExpect(jsonPath("$.data.status").exists());
        mockMvc.perform(get("/api/v1/home/popular").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobGroup").doesNotExist());
    }

    @Test
    void 없는_직군으로_전체보기를_요청하면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/home/popular").param("jobGroup", "ASTRONAUT")
                .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 토큰_없이_홈을_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/home")).andExpect(status().isUnauthorized());
    }

    private TokenResponse newUser() {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        return tokenService.issue(userId);
    }

    private Long userIdOf(TokenResponse tokens) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.userId")).longValue();
    }

    private ResultActions home(TokenResponse tokens) throws Exception {
        return mockMvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    private static long visitorCount(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.todayVisitorCount")).longValue();
    }

    private static String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }
}
