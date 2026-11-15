package com.byarrival.domain.scrap.entity;

/**
 * 보관함에 저장하는 콘텐츠 유형. 상식 용어(TERM)는 기능 보류로 저장하지 않는다(PICK-04·PICK-05).
 */
public enum ScrapTargetType {
    ARTICLE,
    SMALLTALK,
    TERM
}
