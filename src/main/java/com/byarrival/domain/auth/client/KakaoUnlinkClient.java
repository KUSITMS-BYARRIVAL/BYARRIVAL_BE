package com.byarrival.domain.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 회원 탈퇴 시 카카오 연결을 끊는다. Admin 키로 요청하므로 사용자의 카카오 토큰이 필요 없다.
 */
@Slf4j
@Component
public class KakaoUnlinkClient {

    private final RestClient restClient;
    private final SocialAuthProperties.Kakao properties;

    public KakaoUnlinkClient(RestClient socialAuthRestClient, SocialAuthProperties properties) {
        this.restClient = socialAuthRestClient;
        this.properties = properties.kakao();
    }

    /**
     * Admin 키가 없으면 건너뛴다. 카카오가 실패 응답을 주거나 호출에 실패하면 RestClientException이 그대로 나간다.
     *
     * @param kakaoUserId 카카오 회원번호 (로그인 때 저장한 provider_user_id)
     */
    public void unlink(String kakaoUserId) {
        if (!StringUtils.hasText(properties.adminKey())) {
            log.info("카카오 Admin 키가 없어 연결 끊기를 건너뜀");
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", kakaoUserId);
        restClient.post()
            .uri(properties.unlinkUri())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity();
    }
}
