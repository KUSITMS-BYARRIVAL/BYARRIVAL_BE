package com.byarrival.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 보안 필터·DB로 토큰 인증, 재발급, 로그아웃, 약관 동의, 내 정보 조회 흐름을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private Long userId;
    private TokenResponse tokens;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString()));
        userId = user.getId();
        tokens = tokenService.issue(userId);
    }

    @Test
    void 토큰이_없으면_UNAUTHORIZED다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조된_토큰이면_INVALID_TOKEN이다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 만료된_토큰이면_TOKEN_EXPIRED다() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .issuedAt(past.minusSeconds(60))
            .expiresAt(past)
            .build();
        String expired = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void 약관에_동의하기_전에는_내_정보의_약관_동의_여부가_false다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.termsAgreed").value(false))
            .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    void 필수_약관에_모두_동의하면_내_정보의_약관_동의_여부가_true가_된다() throws Exception {
        mockMvc.perform(get("/api/v1/terms").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].type").value("SERVICE"))
            .andExpect(jsonPath("$.data[0].required").value(true))
            .andExpect(jsonPath("$.data[1].type").value("PRIVACY"));

        mockMvc.perform(post("/api/v1/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(agreementBody(true, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].agreed").value(true));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
            .andExpect(jsonPath("$.data.termsAgreed").value(true));
    }

    @Test
    void 필수_약관에_동의하지_않으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/terms/agreements")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(agreementBody(true, false)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));
    }

    @Test
    void 재발급한_뒤_이전_리프레시_토큰은_다시_쓸_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(tokens.refreshToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(tokens.refreshToken())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 같은_리프레시_토큰으로_동시에_재발급하면_한_번만_성공한다() throws Exception {
        int requests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        Callable<Boolean> reissue = () -> {
            start.await();
            try {
                tokenService.reissue(tokens.refreshToken());
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };
        for (int i = 0; i < requests; i++) {
            results.add(executor.submit(reissue));
        }
        start.countDown();

        int successes = 0;
        for (Future<Boolean> result : results) {
            successes += result.get() ? 1 : 0;
        }
        executor.shutdown();
        assertThat(successes).isEqualTo(1);
    }

    @Test
    void 로그아웃하면_그_리프레시_토큰으로_재발급할_수_없고_다시_로그아웃해도_성공한다() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(tokens.refreshToken())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 다른_기기의_리프레시_토큰은_로그아웃해도_유지된다() throws Exception {
        TokenResponse otherDevice = tokenService.issue(userId);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(tokens.refreshToken())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(otherDevice.refreshToken())))
            .andExpect(status().isOk());
    }

    private String agreementBody(boolean serviceAgreed, boolean privacyAgreed) throws Exception {
        String terms = mockMvc.perform(get("/api/v1/terms")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
            .andReturn().getResponse().getContentAsString();
        long serviceId = Long.parseLong(terms.replaceAll(".*?\"termsId\":(\\d+),\"type\":\"SERVICE\".*", "$1"));
        long privacyId = Long.parseLong(terms.replaceAll(".*?\"termsId\":(\\d+),\"type\":\"PRIVACY\".*", "$1"));
        return """
            {"agreements":[{"termsId":%d,"agreed":%s},{"termsId":%d,"agreed":%s}]}
            """.formatted(serviceId, serviceAgreed, privacyId, privacyAgreed);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
