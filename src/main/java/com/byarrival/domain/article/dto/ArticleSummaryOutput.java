package com.byarrival.domain.article.dto;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * AI 요약 응답 JSON(AI-06). 구조화 출력 스키마로도 쓴다. 값은 저장 전에 서버에서 검증한다(AI-02).
 */
public record ArticleSummaryOutput(
    @JsonPropertyDescription("입력받은 기사 제목을 그대로")
    String title,

    @JsonPropertyDescription("입력받은 요약 난이도를 그대로")
    ArticleDifficulty difficulty,

    @JsonPropertyDescription("한 줄 요약. 공백 포함 40자 이내")
    String oneLine,

    @JsonPropertyDescription("요약 3문장. 1: 핵심 사건·변화, 2: 배경·원인·수치, 3: 결과·영향·전망. 각 문장 공백 포함 30자 이내")
    List<String> summary,

    @JsonPropertyDescription("기사의 핵심 키워드 3개. 각 키워드는 공백 포함 10자 이내, # 없이")
    List<String> keywords
) {
}
