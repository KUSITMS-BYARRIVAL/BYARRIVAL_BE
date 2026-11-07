package com.byarrival.domain.article.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크롤링해 정제한 원문 본문. AI 요약·난이도 판정에만 쓰고 끝나면 지운다.
 * 기사 조회가 본문을 읽지 않도록 article과 분리했다.
 */
@Entity
@Table(name = "article_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleContent extends BaseEntity {

    @Id
    private Long articleId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private ArticleContent(Article article, String content) {
        this.article = article;
        this.content = content;
    }

    public static ArticleContent of(Article article, String content) {
        return new ArticleContent(article, content);
    }
}
