package com.byarrival.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 퀴즈 응답 JSON(AI-03). 구조화 출력 스키마로도 쓴다. 값은 저장 전에 서버에서 검증한다(AI-04).
 */
public record QuizOutput(
    @JsonPropertyDescription("입력받은 기사 제목을 그대로")
    String title,

    @JsonPropertyDescription("문제. 공백 포함 50자 이내, 물음표로 끝냄")
    String question,

    @JsonPropertyDescription("선택지 정확히 3개. 각 공백 포함 20자 이내, 서로 다름")
    List<String> choices,

    @JsonPropertyDescription("정답 선택지 번호. 1, 2, 3 중 하나")
    int answer,

    @JsonPropertyDescription("해설. 정답 근거를 한 문장으로, 공백 포함 60자 이내, '~요.'로 끝냄")
    String explanation
) {
}
