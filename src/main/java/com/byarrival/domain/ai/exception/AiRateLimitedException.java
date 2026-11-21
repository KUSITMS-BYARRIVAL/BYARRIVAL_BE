package com.byarrival.domain.ai.exception;

import com.byarrival.common.exception.BusinessException;

/**
 * 요청 한도를 넘어(429) SDK 재시도 후에도 응답을 받지 못한 호출. 토큰을 쓰지 않았으므로 부르는 쪽은 시도 횟수와 따로 셀 수 있다.
 */
public class AiRateLimitedException extends BusinessException {

    public AiRateLimitedException(String message) {
        super(AiErrorCode.AI_CALL_FAILED, message);
    }
}
