package com.byarrival.domain.article.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 같은 사건 묶기 호출 설정. 요약이 끝난 서비스일 기사 전체를 한 번에 보낸다.
 *
 * @param model      Claude 모델 ID
 * @param maxTokens  응답 토큰 상한(생각 토큰 포함)
 * @param effort     생각 깊이. low, medium, high, xhigh, max
 * @param maxRetries 응답이 검증에 실패했을 때 다시 요청하는 횟수(최대 3, AI-05)
 */
@ConfigurationProperties(prefix = "ai.story")
public record ArticleStoryProperties(
    String model,
    long maxTokens,
    String effort,
    int maxRetries
) {

    public ArticleStoryProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.story.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.story.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.story.effort는 비어 있을 수 없습니다.");
        }
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("ai.story.max-retries는 0 이상 3 이하여야 합니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
