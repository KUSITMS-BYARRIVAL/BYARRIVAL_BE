package com.byarrival.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직군 12종 (ONB-11). 화면 배치 순서로 선언한다. 학생은 희망 직군으로 고른다.
 */
@Getter
@RequiredArgsConstructor
public enum JobGroup {
    PLANNING_MARKETING("기획·마케팅"),
    HR_ADMIN_LEGAL("인사·총무·법무"),
    FINANCE_ACCOUNTING("금융·회계"),
    IT_DATA("IT개발·데이터"),
    DESIGN("디자인"),
    SALES_SERVICE("영업·서비스·상담"),
    MANUFACTURING_LOGISTICS("제조·물류·건설"),
    MEDICAL("의료"),
    RESEARCH_EDUCATION("연구·교육"),
    MEDIA_CULTURE_SPORTS("미디어·문화·스포츠"),
    PUBLIC_WELFARE("공공·복지"),
    ETC_FREELANCE("기타·프리랜서");

    /** 한 사람이 고를 수 있는 최대 직군 수 */
    public static final int MAX_SELECTION = 2;

    private final String displayName;
}
