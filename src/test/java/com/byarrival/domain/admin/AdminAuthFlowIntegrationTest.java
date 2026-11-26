package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 DB와 보안 필터로 어드민 로그인·접근 제어를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "admin.auth.allowed-origins=https://admin.example.com")
class AdminAuthFlowIntegrationTest {

    private static final String LOGIN = "/api/admin/auth/login";
    private static final String ME = "/api/admin/auth/me";
    private static final String LOGOUT = "/api/admin/auth/logout";
    private static final String PASSWORD = "correct-password";
    // 테스트마다 BCrypt를 다시 돌리지 않게 한 번만 만든다
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private String email;

    @BeforeEach
    void setUp() {
        email = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", email, PASSWORD_HASH);
    }

    @Test
    void 로그인하면_HttpOnly_쿠키를_주고_본문에는_토큰을_넣지_않는다() throws Exception {
        MvcResult result = login(email, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(email))
            .andExpect(jsonPath("$.data.role").value("OPERATOR"))
            .andExpect(jsonPath("$.data.token").doesNotExist())
            .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(AdminCookieFactory.COOKIE_NAME + "=", "HttpOnly", "Secure", "SameSite=Lax",
            "Path=/api/admin", "Max-Age=86400");
    }

    @Test
    void 쿠키로_내_정보를_조회하고_이메일_대소문자는_구분하지_않는다() throws Exception {
        Cookie cookie = loginCookie(email.toUpperCase());

        mockMvc.perform(get(ME).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void 비밀번호가_틀리거나_없는_이메일이면_같은_오류다() throws Exception {
        login(email, "wrong-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
        login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
    }

    @Test
    void 이메일_형식이_틀리면_400이다() throws Exception {
        login("not-email", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 여러_번_실패해도_잠기지_않고_맞는_비밀번호로_로그인할_수_있다() throws Exception {
        for (int i = 0; i < 6; i++) {
            login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
        }

        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 쿠키_없이_호출하면_401이다() throws Exception {
        mockMvc.perform(get(ME))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조한_쿠키는_401이다() throws Exception {
        mockMvc.perform(get(ME).cookie(new Cookie(AdminCookieFactory.COOKIE_NAME, "forged.token.value")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 앱_회원_토큰으로는_어드민에_접근할_수_없다() throws Exception {
        String appToken = appToken();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(ME).cookie(new Cookie(AdminCookieFactory.COOKIE_NAME, appToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 어드민_토큰으로는_앱_API에_접근할_수_없다() throws Exception {
        Cookie cookie = loginCookie(email);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃하면_쿠키를_지우고_이전_토큰을_쓸_수_없다() throws Exception {
        Cookie cookie = loginCookie(email);

        MvcResult result = mockMvc.perform(post(LOGOUT).cookie(cookie))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        mockMvc.perform(get(ME).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void 비활성화한_계정은_로그인도_기존_토큰도_막힌다() throws Exception {
        Cookie cookie = loginCookie(email);
        jdbcTemplate.update("UPDATE admin_account SET active = FALSE WHERE email = ?", email);

        mockMvc.perform(get(ME).cookie(cookie)).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
    }

    @Test
    void 허용한_어드민_웹은_쿠키를_포함해_호출할_수_있다() throws Exception {
        mockMvc.perform(options(LOGIN)
                .header(HttpHeaders.ORIGIN, "https://admin.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example.com"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 허용하지_않은_사이트의_요청은_쿠키가_있어도_막는다() throws Exception {
        Cookie cookie = loginCookie(email);

        mockMvc.perform(post(LOGOUT).cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example.com"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk());
    }

    private Cookie loginCookie(String loginEmail) throws Exception {
        String setCookie = login(loginEmail, PASSWORD).andExpect(status().isOk()).andReturn()
            .getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        return new Cookie(AdminCookieFactory.COOKIE_NAME, pair.substring(pair.indexOf('=') + 1));
    }

    private ResultActions login(String loginEmail, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(loginEmail, password)));
    }

    private String appToken() {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        return tokenService.issue(userId).accessToken();
    }
}
