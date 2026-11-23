package com.byarrival.domain.admin.entity;

/**
 * 기사 목록 탭(ARTICLE-01).
 */
public enum AdminArticleTab {
    ALL(null),
    EXPOSED(AdminArticleState.EXPOSED),
    HIDDEN(AdminArticleState.HIDDEN),
    ERROR(AdminArticleState.ERROR);

    private final AdminArticleState state;

    AdminArticleTab(AdminArticleState state) {
        this.state = state;
    }

    /**
     * @return 이 탭의 상태. 전체 탭이면 null
     */
    public AdminArticleState state() {
        return state;
    }
}
