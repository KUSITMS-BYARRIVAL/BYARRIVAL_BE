package com.byarrival.domain.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AiConfig {

    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(AnthropicProperties properties) {
        return AnthropicOkHttpClient.builder()
            // 키가 없어도 앱은 뜨게 한다. 호출하면 401로 실패하고 요약 실패로 기록된다
            .apiKey(properties.hasApiKey() ? properties.apiKey() : "missing")
            .timeout(properties.timeout())
            .maxRetries(properties.maxRetries())
            .build();
    }
}
