package com.byarrival.domain.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude API 연결 설정
 *
 * @param apiKey     API 키. 비어 있으면 호출이 인증 실패로 끝난다(로컬·테스트)
 * @param timeout    요청 한 건의 전체 시간 상한. 1ms 이상
 * @param maxRetries 429·5xx·연결 오류일 때 SDK가 다시 시도하는 횟수
 * @param inputPricePerMillionTokens  입력 토큰 100만 개당 가격(USD). 알림의 예상 비용 계산에만 쓴다
 * @param outputPricePerMillionTokens 출력 토큰 100만 개당 가격(USD). 생각 토큰도 출력 가격으로 계산된다
 */
@ConfigurationProperties(prefix = "ai.anthropic")
public record AnthropicProperties(
    String apiKey,
    Duration timeout,
    int maxRetries,
    double inputPricePerMillionTokens,
    double outputPricePerMillionTokens
) {

    public AnthropicProperties {
        if (timeout == null || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("ai.anthropic.timeout은 1ms 이상이어야 합니다.");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("ai.anthropic.max-retries는 0 이상이어야 합니다.");
        }
    }

    public double estimateCost(long inputTokens, long outputTokens) {
        return inputTokens / 1_000_000.0 * inputPricePerMillionTokens
            + outputTokens / 1_000_000.0 * outputPricePerMillionTokens;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
