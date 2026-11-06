package com.byarrival.domain.article.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RSS로 수집한 기사. 원본을 보존하고 가공 결과(정제 본문, 읽기시간, 카테고리)를 함께 둔다.
 */
@Entity
@Table(name = "article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity {

    public static final int MAX_GUID_LENGTH = 500;
    public static final int MAX_ORIGINAL_URL_LENGTH = 1000;
    public static final int MAX_TITLE_LENGTH = 300;
    public static final int MAX_RSS_CATEGORY_LENGTH = 50;
    public static final int MAX_PRESS_NAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long feedId;

    // 매핑되지 않으면 NULL (검수 대상)
    private Long categoryId;

    @Column(length = MAX_GUID_LENGTH)
    private String guid;

    @Column(nullable = false, length = MAX_ORIGINAL_URL_LENGTH)
    private String originalUrl;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_PRESS_NAME_LENGTH)
    private String pressName;

    @Column(length = MAX_RSS_CATEGORY_LENGTH)
    private String rssCategory;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(columnDefinition = "TEXT")
    private String cleanedContent;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(precision = 7, scale = 3)
    private BigDecimal readingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleProcessStatus processStatus;

    @Builder(builderMethodName = "collected")
    private Article(Long feedId, Long categoryId, String guid, String originalUrl, String title, String pressName,
        String rssCategory, LocalDateTime publishedAt, String rawContent, LocalDate serviceDate) {
        this.feedId = feedId;
        this.categoryId = categoryId;
        this.guid = guid;
        this.originalUrl = originalUrl;
        this.title = title;
        this.pressName = pressName;
        this.rssCategory = rssCategory;
        this.publishedAt = publishedAt;
        this.rawContent = rawContent;
        this.serviceDate = serviceDate;
        this.processStatus = ArticleProcessStatus.COLLECTED;
    }

    public void markCleaned(String cleanedContent, BigDecimal readingMinutes) {
        this.cleanedContent = cleanedContent;
        this.readingMinutes = readingMinutes;
        this.processStatus = ArticleProcessStatus.CLEANED;
    }
}
