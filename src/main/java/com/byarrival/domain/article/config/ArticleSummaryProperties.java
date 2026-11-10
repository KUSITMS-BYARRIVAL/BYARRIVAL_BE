package com.byarrival.domain.article.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 3줄 요약 호출 설정
 *
 * @param model       요약에 쓰는 Claude 모델 ID
 * @param maxTokens   응답 토큰 상한(생각 토큰 포함). 넘으면 JSON이 잘려 실패한다
 * @param effort      생각 깊이. low, medium, high, xhigh, max
 * @param concurrency 동시에 요약하는 기사 수
 * @param maxRetries  검증에 실패했을 때 어긴 규칙을 알려 주고 다시 요청하는 횟수. 최초 호출 이후 최대 3회(AI-05)
 */
@ConfigurationProperties(prefix = "ai.summary")
public record ArticleSummaryProperties(
    String model,
    long maxTokens,
    String effort,
    int concurrency,
    int maxRetries
) {

    public ArticleSummaryProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.summary.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.summary.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.summary.effort는 비어 있을 수 없습니다.");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("ai.summary.concurrency는 0보다 커야 합니다.");
        }
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("ai.summary.max-retries는 0 이상 3 이하여야 합니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
