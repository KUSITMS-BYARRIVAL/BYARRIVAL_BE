package com.byarrival.domain.route.dto.response;

/**
 * 많이 담은 뉴스 순위 상태.
 */
public enum PopularArticlesStatus {
    /** 순위를 보여 준다 */
    RANKED,
    /** 그날 기사를 담은 사용자가 최소 인원보다 적어 순위 대신 집계중 안내를 보여 준다(HOME-07) */
    AGGREGATING
}
