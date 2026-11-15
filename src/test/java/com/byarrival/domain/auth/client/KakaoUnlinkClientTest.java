package com.byarrival.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class KakaoUnlinkClientTest {

    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

    private MockRestServiceServer server;

    @Test
    void Admin_키로_카카오_회원번호의_연결을_끊는다() {
        KakaoUnlinkClient client = client("admin-key");
        server.expect(requestTo(UNLINK_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK admin-key"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formDataContains(Map.of("target_id_type", "user_id", "target_id", "12345")))
            .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        client.unlink("12345");

        server.verify();
    }

    @Test
    void Admin_키가_없으면_카카오를_호출하지_않는다() {
        KakaoUnlinkClient client = client("");

        client.unlink("12345");

        server.verify();
    }

    @Test
    void 카카오가_실패_응답을_주면_예외가_난다() {
        KakaoUnlinkClient client = client("admin-key");
        server.expect(requestTo(UNLINK_URI))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-101,\"msg\":\"not registered user\"}"));

        assertThatThrownBy(() -> client.unlink("12345")).isInstanceOf(RestClientException.class);
    }

    @Test
    void 카카오_서버_오류면_예외가_난다() {
        KakaoUnlinkClient client = client("admin-key");
        server.expect(requestTo(UNLINK_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.unlink("12345")).isInstanceOf(RestClientException.class);
    }

    private KakaoUnlinkClient client(String adminKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SocialAuthProperties properties = new SocialAuthProperties(Duration.ofSeconds(3), Duration.ofSeconds(5),
            new SocialAuthProperties.Kakao(null, null, UNLINK_URI, adminKey), null);
        return new KakaoUnlinkClient(builder.build(), properties);
    }
}
