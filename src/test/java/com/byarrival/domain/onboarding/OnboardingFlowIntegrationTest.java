package com.byarrival.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
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
 * 실제 DB와 보안 필터로 관심 분야 목록, 온보딩 단계별 저장, 입력 검증, 온보딩 완료를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OnboardingFlowIntegrationTest {

    private static final String ONBOARDING = "/api/v1/users/me/onboarding";

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
    void 관심_분야_목록은_활성_카테고리만_돌려준다() throws Exception {
        Long inactiveId = insertCategory(false);

        perform(get("/api/v1/categories"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id", hasItems(economyId.intValue(), societyId.intValue())))
            .andExpect(jsonPath("$.data[*].id", not(hasItem(inactiveId.intValue()))));
    }

    @Test
    void 화면마다_보낸_항목만_저장하고_지금까지_저장한_값을_돌려준다() throws Exception {
        perform(patch(ONBOARDING), "{\"nickname\":\"  도착이 \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("도착이"))
            .andExpect(jsonPath("$.data.travelType").doesNotExist());

        perform(patch(ONBOARDING), "{\"travelType\":\"STUDENT\",\"commuteStartTime\":\"08:30\"}")
            .andExpect(status().isOk());
        perform(patch(ONBOARDING), "{\"defaultCommuteMinutes\":25}")
            .andExpect(status().isOk());

        perform(get(ONBOARDING), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("도착이"))
            .andExpect(jsonPath("$.data.travelType").value("STUDENT"))
            .andExpect(jsonPath("$.data.commuteStartTime").value("08:30"))
            .andExpect(jsonPath("$.data.defaultCommuteMinutes").value(25))
            .andExpect(jsonPath("$.data.categoryIds").isEmpty())
            .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    void 관심_분야는_보낸_목록으로_통째로_바꾸고_중복은_한_번만_저장한다() throws Exception {
        perform(patch(ONBOARDING), categories(economyId, societyId)).andExpect(status().isOk());

        perform(patch(ONBOARDING), categories(societyId, societyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.categoryIds.length()").value(1))
            .andExpect(jsonPath("$.data.categoryIds[0]").value(societyId));
    }

    @Test
    void 닉네임_규칙에_어긋나면_INVALID_NICKNAME으로_응답한다() throws Exception {
        for (String nickname : new String[]{"", "   ", "열한글자닉네임입니다요", "도착!", "ㄷㅊ", "도착 이"}) {
            perform(patch(ONBOARDING), "{\"nickname\":\"%s\"}".formatted(nickname))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        }
    }

    @Test
    void 기본_이동시간이_범위나_단위에_맞지_않으면_INVALID_COMMUTE_MINUTES로_응답한다() throws Exception {
        for (int minutes : new int[]{0, 3, 12, 95}) {
            perform(patch(ONBOARDING), "{\"defaultCommuteMinutes\":%d}".formatted(minutes))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COMMUTE_MINUTES"));
        }
        perform(patch(ONBOARDING), "{\"defaultCommuteMinutes\":90}").andExpect(status().isOk());
    }

    @Test
    void 하나라도_규칙에_어긋나면_함께_보낸_값도_저장하지_않는다() throws Exception {
        perform(patch(ONBOARDING), "{\"nickname\":\"도착이\",\"defaultCommuteMinutes\":7}")
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(userId).orElseThrow().getNickname()).isNull();
    }

    @Test
    void 없거나_비활성인_관심_분야를_고르면_INVALID_CATEGORY로_응답한다() throws Exception {
        Long inactiveId = insertCategory(false);

        perform(patch(ONBOARDING), categories(economyId, inactiveId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
        perform(patch(ONBOARDING), categories(economyId, Long.MAX_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }

    @Test
    void 관심_분야를_빈_목록으로_보내면_INVALID_INPUT_VALUE로_응답한다() throws Exception {
        perform(patch(ONBOARDING), "{\"categoryIds\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 이동_유형이나_시각_형식이_잘못되면_INVALID_REQUEST_BODY로_응답한다() throws Exception {
        perform(patch(ONBOARDING), "{\"travelType\":\"RETIRED\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        perform(patch(ONBOARDING), "{\"commuteStartTime\":\"8시 반\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void 직군은_보낸_순서대로_통째로_바꾸고_중복은_처음_순서로_한_번만_저장한다() throws Exception {
        perform(patch(ONBOARDING), "{\"jobGroups\":[\"IT_DATA\",\"FINANCE_ACCOUNTING\"]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobGroups[0]").value("IT_DATA"))
            .andExpect(jsonPath("$.data.jobGroups[1]").value("FINANCE_ACCOUNTING"));

        perform(patch(ONBOARDING), "{\"jobGroups\":[\"DESIGN\",\"DESIGN\"]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobGroups.length()").value(1))
            .andExpect(jsonPath("$.data.jobGroups[0]").value("DESIGN"));

        perform(get(ONBOARDING), null)
            .andExpect(jsonPath("$.data.jobGroups.length()").value(1))
            .andExpect(jsonPath("$.data.jobGroups[0]").value("DESIGN"));
    }

    @Test
    void 직군을_비우거나_3개_이상_보내면_INVALID_INPUT_VALUE로_응답한다() throws Exception {
        perform(patch(ONBOARDING), "{\"jobGroups\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        perform(patch(ONBOARDING), "{\"jobGroups\":[\"DESIGN\",\"MEDICAL\",\"IT_DATA\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 없는_직군_코드를_보내면_INVALID_REQUEST_BODY로_응답한다() throws Exception {
        perform(patch(ONBOARDING), "{\"jobGroups\":[\"ASTRONAUT\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void 직군이_없으면_나머지_필수값이_있어도_온보딩을_완료할_수_없다() throws Exception {
        perform(patch(ONBOARDING), "{\"travelType\":\"WORKER\",\"commuteStartTime\":\"08:00\","
            + "\"defaultCommuteMinutes\":30,\"categoryIds\":[%d]}".formatted(economyId))
            .andExpect(status().isOk());

        perform(post(ONBOARDING + "/complete"), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"));
    }

    @Test
    void 필수값이_빠졌으면_온보딩을_완료할_수_없다() throws Exception {
        perform(patch(ONBOARDING), "{\"travelType\":\"WORKER\",\"commuteStartTime\":\"08:00\","
            + "\"defaultCommuteMinutes\":30}").andExpect(status().isOk());

        perform(post(ONBOARDING + "/complete"), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"));
        assertThat(userRepository.findById(userId).orElseThrow().isOnboardingCompleted()).isFalse();
    }

    @Test
    void 필수값을_모두_저장하면_온보딩을_완료하고_다시_요청해도_처음_완료_시각을_유지한다() throws Exception {
        perform(patch(ONBOARDING), "{\"travelType\":\"WORKER\",\"commuteStartTime\":\"07:50\","
            + "\"defaultCommuteMinutes\":30,\"categoryIds\":[%d],\"jobGroups\":[\"ETC_FREELANCE\"]}".formatted(economyId))
            .andExpect(status().isOk());

        perform(post(ONBOARDING + "/complete"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.onboardingCompleted").value(true))
            .andExpect(jsonPath("$.data.jobGroups[0]").value("ETC_FREELANCE"));
        User completed = userRepository.findById(userId).orElseThrow();
        LocalDateTime onboardedAt = completed.getOnboardedAt();
        assertThat(onboardedAt).isNotNull();
        assertThat(completed.getTravelType()).isEqualTo(TravelType.WORKER);
        assertThat(completed.getCommuteStartTime()).isEqualTo(LocalTime.of(7, 50));

        perform(post(ONBOARDING + "/complete"), null).andExpect(status().isOk());
        assertThat(userRepository.findById(userId).orElseThrow().getOnboardedAt()).isEqualTo(onboardedAt);
        perform(get("/api/v1/users/me"), null)
            .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 토큰_없이_온보딩_API를_호출하면_401로_응답한다() throws Exception {
        mockMvc.perform(get(ONBOARDING)).andExpect(status().isUnauthorized());
    }

    private Long insertCategory(boolean active) {
        String name = "분야-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order, active) VALUES (?, 1, ?) RETURNING id", Long.class, name,
            active);
    }

    private String categories(Long... ids) {
        return "{\"categoryIds\":[%s]}".formatted(
            String.join(",", Arrays.stream(ids).map(String::valueOf).toList()));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
