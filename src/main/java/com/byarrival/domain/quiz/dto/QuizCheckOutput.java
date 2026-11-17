package com.byarrival.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 퀴즈 검수 응답 JSON(AI-04). 만든 퀴즈를 요약만 보고 다시 풀어 본 결과다.
 */
public record QuizCheckOutput(
    @JsonPropertyDescription("문제에 대한 올바른 답인 선택지 번호 목록(1~3). 없으면 빈 목록")
    List<Integer> correctChoices,

    @JsonPropertyDescription("해설이 올바른 답인 선택지의 근거를 설명하면 true")
    boolean explanationMatches,

    @JsonPropertyDescription("판단 이유 한 문장, 100자 이내")
    String reason
) {
}
