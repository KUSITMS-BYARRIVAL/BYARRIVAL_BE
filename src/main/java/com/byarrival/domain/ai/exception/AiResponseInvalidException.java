package com.byarrival.domain.ai.exception;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import lombok.Getter;

/**
 * 응답은 받았지만 쓸 수 없는 AI 응답(잘림, 거절, JSON 개수 오류). 응답을 받았으므로 토큰은 이미 쓰였고,
 * 배치 비용 집계에 넣을 수 있게 사용량을 함께 담는다. 응답을 받지 못한 호출 실패는 {@link AiErrorCode#AI_CALL_FAILED}다.
 */
@Getter
public class AiResponseInvalidException extends BusinessException {

    private final TokenUsage usage;

    public AiResponseInvalidException(TokenUsage usage, String message) {
        super(AiErrorCode.AI_RESPONSE_INVALID, message);
        this.usage = usage;
    }

    /**
     * 실패한 호출이 쓴 토큰. 응답을 받지 못한 호출은 쓴 토큰을 알 수 없어 0으로 센다.
     */
    public static TokenUsage usageOf(BusinessException e) {
        return e instanceof AiResponseInvalidException invalid ? invalid.usage : TokenUsage.ZERO;
    }
}
