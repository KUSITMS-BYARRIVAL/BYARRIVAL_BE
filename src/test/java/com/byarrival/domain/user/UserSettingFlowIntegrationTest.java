package com.byarrival.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 DB와 보안 필터로 마이 화면의 프로필·개인 설정 조회와 변경을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class UserSettingFlowIntegrationTest {

    private static final String ME = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private TokenResponse tokens;
    private Long economyId;
    private Long societyId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        tokens = tokenService.issue(userId);
        economyId = insertCategory(true);
        societyId = insertCategory(true);
    }

    // 다른 테스트가 category를 지울 수 있도록 관심 분야 행을 남기지 않는다.
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM user_interest_category");
        jdbcTemplate.update("DELETE FROM user_job_group");
    }

    @Test
    void 내_정보는_프로필과_개인_설정을_함께_돌려준다() throws Exception {
        perform(patch(ME), "{\"nickname\":\"도착이\",\"travelType\":\"WORKER\",\"defaultCommuteMinutes\":40}")
            .andExpect(status().isOk());
        perform(put(ME + "/interest-categories"), categories(economyId)).andExpect(status().isOk());
        perform(put(ME + "/job-groups"), "{\"jobGroups\":[\"DESIGN\"]}").andExpect(status().isOk());

        perform(get(ME), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("도착이"))
            .andExpect(jsonPath("$.data.travelType").value("WORKER"))
            .andExpect(jsonPath("$.data.commuteStartTime").doesNotExist())
            .andExpect(jsonPath("$.data.defaultCommuteMinutes").value(40))
            .andExpect(jsonPath("$.data.weeklyCommute").isEmpty())
            .andExpect(jsonPath("$.data.categoryIds[0]").value(economyId))
            .andExpect(jsonPath("$.data.jobGroups[0]").value("DESIGN"))
            .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    void 내_정보는_보낸_항목만_바꾼다() throws Exception {
        perform(patch(ME), "{\"nickname\":\"도착이\",\"defaultCommuteMinutes\":30}").andExpect(status().isOk());

        perform(patch(ME), "{\"travelType\":\"STUDENT\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("도착이"))
            .andExpect(jsonPath("$.data.travelType").value("STUDENT"))
            .andExpect(jsonPath("$.data.defaultCommuteMinutes").value(30));
    }

    @Test
    void 내_정보_수정으로는_출근_시각을_바꾸지_않고_요일별_설정에서만_바꾼다() throws Exception {
        perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("MONDAY", "08:00", 30) + "]}")
            .andExpect(status().isOk());

        perform(patch(ME), "{\"commuteStartTime\":\"07:40\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weeklyCommute[0].startTime").value("08:00"));
        perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("MONDAY", "07:40", 30) + "]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weeklyCommute[0].startTime").value("07:40"));
    }

    @Test
    void 닉네임이나_기본_이동시간이_규칙에_어긋나면_아무것도_저장하지_않는다() throws Exception {
        perform(patch(ME), "{\"nickname\":\"도착!\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        perform(patch(ME), "{\"nickname\":\"도착이\",\"defaultCommuteMinutes\":95}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_COMMUTE_MINUTES"));

        assertThat(userRepository.findById(userId).orElseThrow().getNickname()).isNull();
    }

    @Test
    void 관심_분야는_통째로_바꾸고_비활성이거나_빈_목록이면_거절한다() throws Exception {
        perform(put(ME + "/interest-categories"), categories(economyId, societyId)).andExpect(status().isOk());
        perform(put(ME + "/interest-categories"), categories(societyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.categoryIds.length()").value(1))
            .andExpect(jsonPath("$.data.categoryIds[0]").value(societyId));

        perform(put(ME + "/interest-categories"), categories(insertCategory(false)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
        perform(put(ME + "/interest-categories"), "{\"categoryIds\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 요일별_설정은_보낸_요일만_이동하는_날로_저장하고_나머지는_비운다() throws Exception {
        perform(put(ME + "/weekly-commute"), "{\"days\":["
            + day("FRIDAY", "09:00", 20) + "," + day("MONDAY", "08:00", 30) + "," + day("WEDNESDAY", "08:30", 120)
            + "]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weeklyCommute.length()").value(3))
            .andExpect(jsonPath("$.data.weeklyCommute[0].dayOfWeek").value("MONDAY"))
            .andExpect(jsonPath("$.data.weeklyCommute[0].startTime").value("08:00"))
            .andExpect(jsonPath("$.data.weeklyCommute[0].minutes").value(30))
            .andExpect(jsonPath("$.data.weeklyCommute[2].dayOfWeek").value("FRIDAY"));

        perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("TUESDAY", "07:40", 5) + "]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.weeklyCommute.length()").value(1))
            .andExpect(jsonPath("$.data.weeklyCommute[0].dayOfWeek").value("TUESDAY"));
        Integer mondayMinutes = jdbcTemplate.queryForObject(
            "SELECT mon_minutes FROM users WHERE id = ?", Integer.class, userId);
        assertThat(mondayMinutes).isNull();
    }

    @Test
    void 이동하는_요일이_하나도_없으면_NO_COMMUTE_DAY로_응답한다() throws Exception {
        perform(put(ME + "/weekly-commute"), "{\"days\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("NO_COMMUTE_DAY"));
    }

    @Test
    void 요일별_이동시간이_범위나_단위에_맞지_않거나_요일이_겹치면_INVALID_WEEKLY_COMMUTE로_응답한다() throws Exception {
        for (int minutes : new int[]{0, 3, 12, 125}) {
            perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("MONDAY", "08:00", minutes) + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEEKLY_COMMUTE"));
        }
        perform(put(ME + "/weekly-commute"), "{\"days\":["
            + day("MONDAY", "08:00", 30) + "," + day("MONDAY", "09:00", 30) + "]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_WEEKLY_COMMUTE"));
    }

    @Test
    void 요일별_시각_형식이_잘못되거나_값이_빠지면_입력_오류로_응답한다() throws Exception {
        perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("MONDAY", "8시", 30) + "]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        perform(put(ME + "/weekly-commute"), "{\"days\":[{\"dayOfWeek\":\"MONDAY\",\"minutes\":30}]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 직군을_바꾸면_남는_직군은_앞에_새_직군은_뒤에_둔다() throws Exception {
        perform(put(ME + "/job-groups"), "{\"jobGroups\":[\"IT_DATA\",\"DESIGN\"]}").andExpect(status().isOk());

        perform(put(ME + "/job-groups"), "{\"jobGroups\":[\"MEDICAL\",\"DESIGN\"]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobGroups[0]").value("DESIGN"))
            .andExpect(jsonPath("$.data.jobGroups[1]").value("MEDICAL"));
    }

    @Test
    void 직군을_비우거나_3개_이상_보내면_INVALID_INPUT_VALUE로_응답한다() throws Exception {
        perform(put(ME + "/job-groups"), "{\"jobGroups\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        perform(put(ME + "/job-groups"), "{\"jobGroups\":[\"DESIGN\",\"MEDICAL\",\"IT_DATA\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 온보딩을_처음_완료하면_출근_시각과_기본_이동시간을_월요일부터_금요일까지_넣는다() throws Exception {
        perform(patch(ME + "/onboarding"), "{\"travelType\":\"WORKER\",\"commuteStartTime\":\"07:50\","
            + "\"defaultCommuteMinutes\":35,\"categoryIds\":[%d],\"jobGroups\":[\"DESIGN\"]}".formatted(economyId))
            .andExpect(status().isOk());

        perform(post(ME + "/onboarding/complete"), null).andExpect(status().isOk());

        perform(get(ME), null)
            .andExpect(jsonPath("$.data.weeklyCommute.length()").value(5))
            .andExpect(jsonPath("$.data.weeklyCommute[0].dayOfWeek").value("MONDAY"))
            .andExpect(jsonPath("$.data.weeklyCommute[0].startTime").value("07:50"))
            .andExpect(jsonPath("$.data.weeklyCommute[0].minutes").value(35))
            .andExpect(jsonPath("$.data.weeklyCommute[4].dayOfWeek").value("FRIDAY"));

        // 마이에서 바꾼 뒤 완료 요청이 다시 와도 덮어쓰지 않는다
        perform(put(ME + "/weekly-commute"), "{\"days\":[" + day("SATURDAY", "10:00", 15) + "]}")
            .andExpect(status().isOk());
        perform(post(ME + "/onboarding/complete"), null).andExpect(status().isOk());
        perform(get(ME), null)
            .andExpect(jsonPath("$.data.weeklyCommute.length()").value(1))
            .andExpect(jsonPath("$.data.weeklyCommute[0].dayOfWeek").value("SATURDAY"));
    }

    @Test
    void 토큰_없이_설정을_바꾸면_401로_응답한다() throws Exception {
        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"도착이\"}"))
            .andExpect(status().isUnauthorized());
    }

    private Long insertCategory(boolean active) {
        String name = "분야-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order, active) VALUES (?, 1, ?) RETURNING id", Long.class, name,
            active);
    }

    private String categories(Long... ids) {
        StringBuilder body = new StringBuilder("{\"categoryIds\":[");
        for (int i = 0; i < ids.length; i++) {
            body.append(i == 0 ? "" : ",").append(ids[i]);
        }
        return body.append("]}").toString();
    }

    private String day(String dayOfWeek, String startTime, int minutes) {
        return "{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"minutes\":%d}".formatted(dayOfWeek, startTime, minutes);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
