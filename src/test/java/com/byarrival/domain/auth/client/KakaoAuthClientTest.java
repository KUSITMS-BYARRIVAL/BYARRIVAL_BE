package com.byarrival.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoAuthClientTest {

    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final long APP_ID = 1001L;

    private MockRestServiceServer server;
    private KakaoAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoAuthClient(builder.build(), properties(APP_ID));
    }

    @Test
    void 우리_앱에서_발급된_토큰이면_카카오_회원번호를_반환한다() {
        server.expect(requestTo(TOKEN_INFO_URI))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(withSuccess("{\"id\":12345,\"expires_in\":3600,\"app_id\":1001}", MediaType.APPLICATION_JSON));

        assertThat(client.getProviderUserId("access-token")).isEqualTo("12345");
    }

    @Test
    void 다른_앱에서_발급된_토큰이면_인증_실패다() {
        server.expect(requestTo(TOKEN_INFO_URI))
            .andRespond(withSuccess("{\"id\":12345,\"app_id\":9999}", MediaType.APPLICATION_JSON));

        assertAuthError(() -> client.getProviderUserId("access-token"), AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 앱_ID_설정이_없으면_모든_토큰을_거부한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unconfiguredServer = MockRestServiceServer.bindTo(builder).build();
        KakaoAuthClient unconfiguredClient = new KakaoAuthClient(builder.build(), properties(null));
        unconfiguredServer.expect(requestTo(TOKEN_INFO_URI))
            .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        assertAuthError(() -> unconfiguredClient.getProviderUserId("access-token"), AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 유효하지_않은_토큰이면_인증_실패다() {
        server.expect(requestTo(TOKEN_INFO_URI))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-401,\"msg\":\"this access token does not exist\"}"));

        assertAuthError(() -> client.getProviderUserId("expired-token"), AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 카카오_서버_오류면_연결_불가로_응답한다() {
        server.expect(requestTo(TOKEN_INFO_URI)).andRespond(withServerError());

        assertAuthError(() -> client.getProviderUserId("access-token"), AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    private SocialAuthProperties properties(Long appId) {
        return new SocialAuthProperties(Duration.ofSeconds(3), Duration.ofSeconds(5),
            new SocialAuthProperties.Kakao(TOKEN_INFO_URI, appId), null);
    }

    private void assertAuthError(Runnable call, AuthErrorCode errorCode) {
        assertThatThrownBy(call::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
