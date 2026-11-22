package com.byarrival.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 퀴즈 응답 JSON(AI-03). 구조화 출력 스키마로도 쓴다. 값은 저장 전에 서버에서 검증한다(AI-04).
 * 정답 번호는 AI가 고르지 않는다. 정답과 오답을 글로만 받고 서버가 무작위로 섞어 번호를 정한다.
 * 근거 문장(evidence)을 문제보다 먼저 쓰게 해서, 근거 문장에서 정답을 정하고 그 정답을 묻는 문제를 만들게 한다.
 * 제목은 받지 않는다. 퀴즈 명세(AI-03, AI-04)에 없는 확인이고, 따라 쓰기에 실패해 퀴즈를 버리는 일이 잦았다(2026-11-23).
 */
public record QuizOutput(
    @JsonPropertyDescription("정답의 근거. 입력의 한 줄 요약이나 3문장 요약 중 한 문장을 그대로 옮김")
    String evidence,

    @JsonPropertyDescription("문제. 공백 포함 50자 이내, 물음표로 끝냄")
    String question,

    @JsonPropertyDescription("정답 선택지 1개. 공백 포함 20자 이내")
    String answer,

    @JsonPropertyDescription("오답 선택지 정확히 2개. 각 공백 포함 20자 이내, 정답·서로와 다름")
    List<String> wrongAnswers,

    @JsonPropertyDescription("해설. 정답의 근거를 한 문장으로, 공백 포함 60자 이내, '~요.'로 끝냄")
    String explanation
) {
}
