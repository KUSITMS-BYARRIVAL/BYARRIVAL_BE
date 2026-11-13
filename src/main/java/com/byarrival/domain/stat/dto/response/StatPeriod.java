package com.byarrival.domain.stat.dto.response;

/**
 * 이용 기록 요약 기간(STAT-01). WEEK는 이번 주(월~오늘), MONTH는 이번 달(1일~오늘), YEAR는 올해(1월 1일~오늘), ALL은 전체.
 */
public enum StatPeriod {
    WEEK,
    MONTH,
    YEAR,
    ALL
}
