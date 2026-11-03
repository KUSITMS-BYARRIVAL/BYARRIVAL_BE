package com.byarrival.domain.auth.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 소셜 Provider 호출용 RestClient 설정
 */
@Configuration
@EnableConfigurationProperties(SocialAuthProperties.class)
public class SocialAuthClientConfig {

    @Bean
    public RestClient socialAuthRestClient(SocialAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
