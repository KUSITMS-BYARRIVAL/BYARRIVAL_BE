package com.byarrival.domain.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어드민의 기사 숨김·복구 이력(ARTICLE-05). 한 번 남기면 바꾸지 않는다.
 */
@Entity
@Table(name = "article_visibility_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleVisibilityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleVisibilityAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ArticleHideReason reason;

    @Column(length = 100)
    private String memo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private ArticleVisibilityHistory(Long articleId, Long adminId, ArticleVisibilityAction action,
        ArticleHideReason reason, String memo, LocalDateTime createdAt) {
        this.articleId = articleId;
        this.adminId = adminId;
        this.action = action;
        this.reason = reason;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    public static ArticleVisibilityHistory hide(Long articleId, Long adminId, ArticleHideReason reason, String memo,
        LocalDateTime now) {
        return new ArticleVisibilityHistory(articleId, adminId, ArticleVisibilityAction.HIDE, reason, memo, now);
    }

    public static ArticleVisibilityHistory restore(Long articleId, Long adminId, LocalDateTime now) {
        return new ArticleVisibilityHistory(articleId, adminId, ArticleVisibilityAction.RESTORE, null, null, now);
    }
}
