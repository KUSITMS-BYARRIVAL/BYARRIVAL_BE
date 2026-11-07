package com.byarrival.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Feed별 언론사 카테고리를 서비스 카테고리로 바꾸는 매핑 규칙
 */
@Entity
@Table(name = "category_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long feedId;

    @Column(nullable = false, length = 50)
    private String rssCategory;

    @Column(nullable = false)
    private Long categoryId;

    private CategoryMapping(Long feedId, String rssCategory, Long categoryId) {
        this.feedId = feedId;
        this.rssCategory = rssCategory;
        this.categoryId = categoryId;
    }

    public static CategoryMapping create(Long feedId, String rssCategory, Long categoryId) {
        return new CategoryMapping(feedId, rssCategory, categoryId);
    }
}
