package com.byarrival.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.exception.GlobalExceptionHandler;
import com.byarrival.domain.auth.dto.response.SocialLoginResponse;
import com.byarrival.domain.auth.dto.response.TokenResponse;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import com.byarrival.domain.auth.service.AuthService;
import com.byarrival.domain.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AuthService authService;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        tokenService = mock(TokenService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, tokenService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void 소셜_로그인에_성공하면_사용자_ID와_신규_회원_여부를_반환한다() throws Exception {
        given(authService.socialLogin(any()))
            .willReturn(new SocialLoginResponse(1L, true, false, false, "access-token", "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"KAKAO\",\"token\":\"access-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.isNewUser").value(true))
            .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
            .andExpect(jsonPath("$.data.termsAgreed").value(false))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void 리프레시_토큰으로_새_토큰을_발급한다() throws Exception {
        given(tokenService.reissue("refresh-token")).willReturn(TokenResponse.of("new-access", "new-refresh"));

        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("new-access"))
            .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    void 재발급할_수_없는_리프레시_토큰이면_401을_반환한다() throws Exception {
        given(tokenService.reissue("revoked")).willThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"revoked\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 소셜_인증에_실패하면_401과_에러코드를_반환한다() throws Exception {
        given(authService.socialLogin(any())).willThrow(new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\",\"token\":\"identity-token\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("SOCIAL_AUTH_FAILED"));
    }

    @Test
    void 토큰이_비어_있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"KAKAO\",\"token\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.data[0].field").value("token"));
    }

    @Test
    void 지원하지_않는_Provider면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"NAVER\",\"token\":\"access-token\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }
}
