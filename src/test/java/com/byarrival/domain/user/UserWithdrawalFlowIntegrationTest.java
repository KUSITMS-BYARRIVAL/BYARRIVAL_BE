package com.byarrival.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.monitoring.ServerErrorNotifier;
import com.byarrival.domain.auth.client.KakaoUnlinkClient;
import com.byarrival.domain.auth.client.SocialAuthClient;
import com.byarrival.domain.auth.client.SocialAuthClients;
import com.byarrival.domain.auth.dto.request.SocialLoginRequest;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.AppleTokenService;
import com.byarrival.domain.auth.service.AuthService;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.notification.repository.DeviceTokenRepository;
import com.byarrival.domain.term.service.TermService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.domain.user.service.UserService;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

/**
 * 실제 DB와 보안 필터로 탈퇴(인증 정보 제거, 데이터 보관), 보관 기간 안 재가입 복원, 기간이 지난 회원 파기를 확인한다.
 */
@SpringBootTest(properties = "notification.push.fcm-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class UserWithdrawalFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private TermService termService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KakaoUnlinkClient kakaoUnlinkClient;

    @MockitoBean
    private AppleTokenService appleTokenService;

    @MockitoBean
    private ServerErrorNotifier serverErrorNotifier;

    private String providerUserId;
    private Long userId;
    private TokenResponse tokens;
    private AuthService authService;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        providerUserId = UUID.randomUUID().toString();
        userId = userRepository.save(User.signUp(SocialProvider.KAKAO, providerUserId)).getId();
        tokens = tokenService.issue(userId);
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "탈퇴-" + UUID.randomUUID().toString().substring(0, 8));
        // 소셜 토큰 검증만 가짜로 두고, 회원 조회·복원·가입은 실제 서비스로 확인한다.
        SocialAuthClient kakao = fakeClient(SocialProvider.KAKAO);
        SocialAuthClient apple = fakeClient(SocialProvider.APPLE);
        authService = new AuthService(new SocialAuthClients(List.of(kakao, apple)), userService, tokenService,
            termService, appleTokenService);
    }

    // 다른 테스트가 category를 지울 수 있도록 이 테스트의 관심 분야·카테고리를 남기지 않는다.
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM user_interest_category WHERE category_id = ?", categoryId);
        jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    }

    @Test
    void 탈퇴하면_모든_기기의_리프레시_토큰과_푸시_토큰을_없애고_데이터는_보관한다() throws Exception {
        TokenResponse otherDevice = tokenService.issue(userId);
        registerDevice("fcm-" + UUID.randomUUID());
        saveOnboarding();

        perform(delete("/api/v1/users/me"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        User withdrawn = userRepository.findById(userId).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        assertThat(withdrawn.getTravelType()).isEqualTo(TravelType.WORKER);
        assertThat(deviceTokenRepository.existsByUserId(userId)).isFalse();
        for (TokenResponse device : List.of(tokens, otherDevice)) {
            reissue(device.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void 카카오_회원이_탈퇴하면_카카오_연결을_끊는다() throws Exception {
        perform(delete("/api/v1/users/me"), null).andExpect(status().isOk());

        verify(kakaoUnlinkClient).unlink(providerUserId);
        verify(serverErrorNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void 카카오_연결_끊기가_실패해도_탈퇴는_완료하고_서버_에러_채널에_알린다() throws Exception {
        RuntimeException failure = new ResourceAccessException("카카오 연결 실패");
        willThrow(failure).given(kakaoUnlinkClient).unlink(providerUserId);

        perform(delete("/api/v1/users/me"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        verify(serverErrorNotifier).notify(eq("DELETE"), eq("/api/v1/users/me"), eq(failure));
    }

    @Test
    void 애플_회원이_탈퇴하면_카카오를_호출하지_않는다() throws Exception {
        Long appleUserId = userRepository.save(User.signUp(SocialProvider.APPLE, UUID.randomUUID().toString()))
            .getId();
        TokenResponse appleTokens = tokenService.issue(appleUserId);

        mockMvc.perform(delete("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appleTokens.accessToken()))
            .andExpect(status().isOk());

        verify(kakaoUnlinkClient, never()).unlink(any());
        verify(appleTokenService).revoke(argThat(user -> user.getId().equals(appleUserId)));
    }

    @Test
    void 애플_연결_끊기가_실패해도_탈퇴는_완료하고_서버_에러_채널에_알린다() throws Exception {
        Long appleUserId = userRepository.save(User.signUp(SocialProvider.APPLE, UUID.randomUUID().toString()))
            .getId();
        TokenResponse appleTokens = tokenService.issue(appleUserId);
        RuntimeException failure = new ResourceAccessException("Apple 연결 실패");
        willThrow(failure).given(appleTokenService).revoke(any());

        mockMvc.perform(delete("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appleTokens.accessToken()))
            .andExpect(status().isOk());

        assertThat(userRepository.findById(appleUserId).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        verify(serverErrorNotifier).notify(eq("DELETE"), eq("/api/v1/users/me"), eq(failure));
    }

    @Test
    void 탈퇴한_뒤에는_내_정보_조회와_다시_탈퇴_요청이_UNAUTHORIZED다() throws Exception {
        perform(delete("/api/v1/users/me"), null).andExpect(status().isOk());

        perform(get("/api/v1/users/me"), null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        perform(delete("/api/v1/users/me"), null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 보관_기간_안에_같은_소셜_계정으로_로그인하면_같은_계정과_데이터를_복원한다() throws Exception {
        saveOnboarding();
        // 탈퇴 때 revoke한 토큰이 남아 있어도 복원하면 지운다
        jdbcTemplate.update("UPDATE users SET apple_refresh_token = 'revoked' WHERE id = ?", userId);
        perform(delete("/api/v1/users/me"), null).andExpect(status().isOk());

        SocialLoginResponse response = login();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.isNewUser()).isFalse();
        User restored = userRepository.findById(userId).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(restored.getWithdrawnAt()).isNull();
        assertThat(restored.getCommuteStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(restored.getAppleRefreshToken()).isNull();
        assertThat(userService.findInterestCategoryIds(userId)).isNotEmpty();
    }

    @Test
    void 보관_기간이_지난_뒤_로그인하면_새_계정으로_가입한다() throws Exception {
        perform(delete("/api/v1/users/me"), null).andExpect(status().isOk());
        withdrawnAt(userId, LocalDateTime.now().minusMonths(6).minusDays(1));

        SocialLoginResponse response = login();

        assertThat(response.userId()).isNotEqualTo(userId);
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.onboardingCompleted()).isFalse();
    }

    @Test
    void 보관_기간이_지난_탈퇴_회원만_데이터와_함께_파기한다() throws Exception {
        saveOnboarding();
        perform(delete("/api/v1/users/me"), null).andExpect(status().isOk());
        withdrawnAt(userId, LocalDateTime.now().minusMonths(6).minusDays(1));
        Long recentWithdrawn = withdrawnUser(LocalDateTime.now().minusMonths(5));
        Long active = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();

        userService.deleteExpiredWithdrawnUsers();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(count("user_interest_category", userId)).isZero();
        assertThat(count("refresh_token", userId)).isZero();
        assertThat(userRepository.findById(recentWithdrawn)).isPresent();
        assertThat(userRepository.findById(active)).isPresent();
    }

    @Test
    void 토큰_없이_탈퇴를_요청하면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    private SocialLoginResponse login() {
        return authService.socialLogin(new SocialLoginRequest(SocialProvider.KAKAO, providerUserId));
    }

    // 가짜 클라이언트는 받은 토큰을 그대로 소셜 계정 식별자로 쓴다.
    private SocialAuthClient fakeClient(SocialProvider provider) {
        return new SocialAuthClient() {
            @Override
            public SocialProvider provider() {
                return provider;
            }

            @Override
            public String getProviderUserId(String token) {
                return token;
            }
        };
    }

    private void saveOnboarding() throws Exception {
        perform(patch("/api/v1/users/me/onboarding"),
            "{\"travelType\":\"WORKER\",\"commuteStartTime\":\"08:00\",\"defaultCommuteMinutes\":30,"
                + "\"categoryIds\":[%d],\"jobGroups\":[\"IT_DATA\"]}".formatted(categoryId))
            .andExpect(status().isOk());
    }

    private void registerDevice(String fcmToken) throws Exception {
        perform(put("/api/v1/devices"), "{\"fcmToken\":\"%s\",\"platform\":\"ANDROID\"}".formatted(fcmToken))
            .andExpect(status().isOk());
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private Long withdrawnUser(LocalDateTime withdrawnAt) {
        Long id = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?", withdrawnAt, id);
        return id;
    }

    private void withdrawnAt(Long id, LocalDateTime withdrawnAt) {
        jdbcTemplate.update("UPDATE users SET withdrawn_at = ? WHERE id = ?", withdrawnAt, id);
    }

    private int count(String table, Long id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, id);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
