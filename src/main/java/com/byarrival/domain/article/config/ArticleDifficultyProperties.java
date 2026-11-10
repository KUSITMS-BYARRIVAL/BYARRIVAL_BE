package com.byarrival.domain.article.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 난이도 판정 호출 설정. 토큰을 아끼려고 카테고리 하나를 한 번에 판정하고, 본문은 앞부분만 보낸다.
 *
 * @param model      판정에 쓰는 Claude 모델 ID
 * @param maxTokens  응답 토큰 상한(생각 토큰 포함)
 * @param effort     생각 깊이. low, medium, high, xhigh, max
 * @param leadLength 기사마다 보내는 본문 앞부분 글자 수
 * @param maxRetries 판정 응답이 검증에 실패했을 때 다시 요청하는 횟수(최대 3, AI-05)
 */
@ConfigurationProperties(prefix = "ai.difficulty")
public record ArticleDifficultyProperties(
    String model,
    long maxTokens,
    String effort,
    int leadLength,
    int maxRetries
) {

    public ArticleDifficultyProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.difficulty.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.difficulty.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.difficulty.effort는 비어 있을 수 없습니다.");
        }
        if (leadLength <= 0) {
            throw new IllegalArgumentException("ai.difficulty.lead-length는 0보다 커야 합니다.");
        }
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("ai.difficulty.max-retries는 0 이상 3 이하여야 합니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
