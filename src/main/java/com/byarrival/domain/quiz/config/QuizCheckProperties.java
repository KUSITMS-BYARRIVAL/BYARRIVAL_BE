package com.byarrival.domain.quiz.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 만든 퀴즈를 요약만 보고 다시 풀어 정답이 하나로 정해지는지 확인하는 호출 설정(AI-04)
 *
 * @param model     검수에 쓰는 Claude 모델 ID
 * @param maxTokens 응답 토큰 상한(생각 토큰 포함)
 * @param effort    생각 깊이. low, medium, high, xhigh, max
 */
@ConfigurationProperties(prefix = "ai.quiz-check")
public record QuizCheckProperties(String model, long maxTokens, String effort) {

    public QuizCheckProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.quiz-check.model은 비어 있을 수 없습니다.");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.quiz-check.max-tokens는 0보다 커야 합니다.");
        }
        if (effort == null || effort.isBlank()) {
            throw new IllegalArgumentException("ai.quiz-check.effort는 비어 있을 수 없습니다.");
        }
    }

    public OutputConfig.Effort outputEffort() {
        return OutputConfig.Effort.of(effort.toLowerCase());
    }
}
