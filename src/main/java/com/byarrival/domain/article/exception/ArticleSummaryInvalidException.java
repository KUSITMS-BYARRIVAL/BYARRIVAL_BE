package com.byarrival.domain.article.exception;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import lombok.Getter;

/**
 * AI 요약 응답이 검증 규칙을 어겼다(AI-02). 메시지는 어긴 규칙을 줄마다 하나씩 담고, 재요청할 때 AI에게 그대로 알려 준다.
 * 근거 구절이나 숫자처럼 기사 본문을 다시 봐야 고칠 수 있는 규칙을 어겼는지도 함께 담는다.
 * 글자 수·어미·제목처럼 형식만 어겼으면 본문 없이 이전 답만 고치게 해 재요청 비용을 줄인다.
 */
@Getter
public class ArticleSummaryInvalidException extends BusinessException {

    private final boolean needsArticle;

    public ArticleSummaryInvalidException(String problems, boolean needsArticle) {
        super(AiErrorCode.AI_RESPONSE_INVALID, problems);
        this.needsArticle = needsArticle;
    }
}
