package com.byarrival.domain.quiz.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 퀴즈 생성 호출 설정(AI-03)
 *
 * @param model       퀴즈에 쓰는 Claude 모델 ID
 * @param maxTokens   응답 토큰 상한(생각 토큰 포함)
 * @param effort      생각 깊이. low, medium, high, xhigh, max
 * @param concurrency 동시에 퀴즈를 만드는 기사 수
 * @param maxRetries  검증에 실패했을 때 어긴 규칙을 알려 주고 다시 요청하는 횟수(최대 3회, AI-05)
 */
@ConfigurationProperties(prefix = "ai.quiz")
public record QuizProperties(String model, long maxTokens, String effort, int concurrency, int maxRetries) {

    public QuizProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.quiz.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.quiz.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.quiz.effort는 비어 있을 수 없습니다.");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("ai.quiz.concurrency는 0보다 커야 합니다.");
        }
        if (maxRetries < 0 || maxRetries > 3) {
            throw new IllegalArgumentException("ai.quiz.max-retries는 0 이상 3 이하여야 합니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
