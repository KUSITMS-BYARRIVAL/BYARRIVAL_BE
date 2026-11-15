package com.byarrival.domain.smalltalk.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * AI 스몰토크 응답 JSON(TALK-01). 값은 저장 전에 서버에서 검증한다.
 */
public record SmalltalkOutput(
    @JsonPropertyDescription("입력받은 기사 제목을 그대로")
    String title,

    @JsonPropertyDescription("대화 소재로 쓰기 알맞은 기사인지. 정치·종교·젠더·세대 갈등 소재이거나 사실만으로 대화를 열기 어려우면 false")
    boolean suitable,

    @JsonPropertyDescription("대화를 여는 한 문장. 공백 포함 30자 이내. suitable이 false면 빈 문자열")
    String hook
) {
}
