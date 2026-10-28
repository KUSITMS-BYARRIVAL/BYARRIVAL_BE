package com.byarrival.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AppleAuthClientTest {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String KEYS_URI = "https://appleid.apple.com/auth/keys";
    private static final String CLIENT_ID = "com.byarrival.app";
    private static final String KID = "test-kid";
    private static final Instant NOW = Instant.parse("2026-10-28T03:00:00Z");

    private static KeyPair appleKeyPair;
    private static KeyPair otherKeyPair;

    private MockRestServiceServer server;
    private AppleAuthClient client;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        appleKeyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        client = clientWith(CLIENT_ID);
    }

    @Test
    void 서명과_클레임이_유효하면_애플_사용자_식별자를_반환한다() throws Exception {
        expectKeys();
        String token = sign(appleKeyPair, header(KID), claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600), "apple-user"));

        assertThat(client.getProviderUserId(token)).isEqualTo("apple-user");
    }

    @Test
    void 애플_공개키로_서명되지_않은_토큰은_인증_실패다() throws Exception {
        expectKeys();
        String token = sign(otherKeyPair, header(KID), claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 만료된_토큰은_인증_실패다() throws Exception {
        expectKeys();
        String token = sign(appleKeyPair, header(KID), claims(ISSUER, CLIENT_ID, NOW.minusSeconds(1), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_인증_실패다() throws Exception {
        expectKeys();
        String token = sign(appleKeyPair, header(KID),
            claims(ISSUER, "com.other.app", NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 발급자가_애플이_아니면_인증_실패다() throws Exception {
        expectKeys();
        String token = sign(appleKeyPair, header(KID),
            claims("https://evil.example.com", CLIENT_ID, NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 클라이언트_ID_설정이_없으면_모든_토큰을_거부한다() throws Exception {
        client = clientWith("");
        expectKeys();
        String token = sign(appleKeyPair, header(KID), claims(ISSUER, "", NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 공개키_목록에_없는_kid면_인증_실패다() throws Exception {
        expectKeys();
        String token = sign(appleKeyPair, header("unknown-kid"),
            claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void RS256이_아닌_알고리즘은_인증_실패다() {
        String header = "{\"kid\":\"" + KID + "\",\"alg\":\"none\"}";
        String token = encode(header) + "." + encode(claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600), "apple-user"))
            + ".";

        assertAuthError(token, AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void JWT_형식이_아니면_인증_실패다() {
        assertAuthError("not-a-jwt", AuthErrorCode.SOCIAL_AUTH_FAILED);
        assertAuthError("a.b.c", AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    void 애플_공개키_조회에_실패하면_연결_불가로_응답한다() throws Exception {
        server.expect(requestTo(KEYS_URI)).andRespond(withServerError());
        String token = sign(appleKeyPair, header(KID), claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600), "apple-user"));

        assertAuthError(token, AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    private AppleAuthClient clientWith(String clientId) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SocialAuthProperties properties = new SocialAuthProperties(Duration.ofSeconds(3), Duration.ofSeconds(5), null,
            new SocialAuthProperties.Apple(ISSUER, KEYS_URI, clientId));
        return new AppleAuthClient(builder.build(), properties, JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    private void expectKeys() {
        RSAPublicKey publicKey = (RSAPublicKey) appleKeyPair.getPublic();
        String jwks = """
            {"keys":[{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
            """.formatted(KID, encodeUnsigned(publicKey.getModulus()), encodeUnsigned(publicKey.getPublicExponent()));
        server.expect(requestTo(KEYS_URI)).andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON));
    }

    private String header(String kid) {
        return "{\"kid\":\"" + kid + "\",\"alg\":\"RS256\"}";
    }

    private String claims(String iss, String aud, Instant exp, String sub) {
        return """
            {"iss":"%s","aud":"%s","exp":%d,"iat":%d,"sub":"%s"}
            """.formatted(iss, aud, exp.getEpochSecond(), NOW.getEpochSecond(), sub);
    }

    private String sign(KeyPair keyPair, String header, String claims) throws Exception {
        String signingInput = encode(header) + "." + encode(claims);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void assertAuthError(String token, AuthErrorCode errorCode) {
        assertThatThrownBy(() -> client.getProviderUserId(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }
}
