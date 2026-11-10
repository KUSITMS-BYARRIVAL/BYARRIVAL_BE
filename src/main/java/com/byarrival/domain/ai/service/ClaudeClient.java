package com.byarrival.domain.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.Usage;
import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Claude API를 한 번 호출해 JSON 응답을 지정한 타입으로 받는다. 응답 내용의 검증은 호출하는 쪽에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    static final String TOKEN_METRIC = "ai.tokens";

    private final AnthropicClient anthropicClient;
    private final MeterRegistry meterRegistry;

    public <T> ClaudeResult<T> generate(ClaudeRequest request, Class<T> outputType) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
            .model(request.model())
            .maxTokens(request.maxTokens())
            .system(request.systemPrompt())
            .addUserMessage(request.userMessage())
            .outputConfig(new StructuredOutputConfig.Builder<T>()
                .format(outputType)
                .effort(request.effort())
                .build())
            .build();

        StructuredMessage<T> response = call(params);
        TokenUsage usage = recordUsage(request.model(), response.usage());
        StopReason stopReason = response.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stopReason)) {
            // max_tokens면 JSON이 잘렸고, refusal이면 내용이 없다
            throw new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, "stop_reason=" + stopReason);
        }
        List<T> outputs = extractOutputs(response);
        if (outputs.size() != 1) {
            throw new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, "응답 JSON 개수=" + outputs.size());
        }
        return new ClaudeResult<>(outputs.getFirst(), usage);
    }

    // 구조화 출력 JSON은 text()를 부를 때 타입으로 바뀐다. 변환 실패 메시지에는 응답 본문이 들어 있어 종류만 남긴다
    private <T> List<T> extractOutputs(StructuredMessage<T> response) {
        try {
            return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .toList();
        } catch (AnthropicInvalidDataException e) {
            throw new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, e.getClass().getSimpleName());
        }
    }

    // 비용 추적용. 생각 토큰은 출력 토큰에 포함된다
    private TokenUsage recordUsage(String model, Usage usage) {
        meterRegistry.counter(TOKEN_METRIC, "model", model, "type", "input").increment(usage.inputTokens());
        meterRegistry.counter(TOKEN_METRIC, "model", model, "type", "output").increment(usage.outputTokens());
        log.debug("[AI 호출] model={} inputTokens={} outputTokens={}", model, usage.inputTokens(), usage.outputTokens());
        return new TokenUsage(usage.inputTokens(), usage.outputTokens());
    }

    private <T> StructuredMessage<T> call(StructuredMessageCreateParams<T> params) {
        // 예외 메시지에는 요청 내용이 섞일 수 있어 종류와 상태 코드만 남긴다
        try {
            return anthropicClient.messages().create(params);
        } catch (AnthropicServiceException e) {
            throw new BusinessException(AiErrorCode.AI_CALL_FAILED,
                e.getClass().getSimpleName() + " status=" + e.statusCode());
        } catch (AnthropicIoException e) {
            throw new BusinessException(AiErrorCode.AI_CALL_FAILED, e.getClass().getSimpleName());
        } catch (AnthropicInvalidDataException e) {
            // 구조화 출력 JSON을 타입으로 바꾸지 못한 경우
            throw new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, e.getClass().getSimpleName());
        }
    }

    /**
     * 검증 전 응답과 이 호출에 쓴 토큰. 검증에 실패한 응답도 비용은 나가므로 토큰을 함께 돌려준다.
     */
    public record ClaudeResult<T>(T output, TokenUsage usage) {
    }

    /**
     * @param outputTokens 생각 토큰을 포함한 출력 토큰
     */
    public record TokenUsage(long inputTokens, long outputTokens) {
    }

    /**
     * @param model        모델 ID
     * @param maxTokens    응답 토큰 상한(생각 토큰 포함)
     * @param effort       생각 깊이와 토큰 사용량
     * @param systemPrompt 고정 지시문
     * @param userMessage  호출마다 바뀌는 입력
     */
    public record ClaudeRequest(
        String model,
        long maxTokens,
        OutputConfig.Effort effort,
        String systemPrompt,
        String userMessage
    ) {
    }
}
