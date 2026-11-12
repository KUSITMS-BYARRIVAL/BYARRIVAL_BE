package com.byarrival.domain.smalltalk.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 스몰토크 생성 설정(TALK-01)
 *
 * @param model               스몰토크에 쓰는 Claude 모델 ID
 * @param maxTokens           응답 토큰 상한(생각 토큰 포함)
 * @param effort              생각 깊이. low, medium, high, xhigh, max
 * @param maxRetries          검증에 실패했을 때 같은 기사로 다시 요청하는 횟수(명세: 최대 3회)
 * @param perCategory         카테고리당 만드는 스몰토크 수(명세: 최대 2건)
 */
@ConfigurationProperties(prefix = "ai.smalltalk")
public record SmalltalkProperties(String model, long maxTokens, String effort, int maxRetries, int perCategory) {

    public SmalltalkProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.smalltalk.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.smalltalk.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.smalltalk.effort는 비어 있을 수 없습니다.");
        }
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("ai.smalltalk.max-retries는 0 이상 3 이하여야 합니다.");
        }
        if (perCategory <= 0) {
            throw new IllegalArgumentException("ai.smalltalk.per-category는 0보다 커야 합니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
