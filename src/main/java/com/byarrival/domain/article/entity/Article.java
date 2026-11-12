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
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * RSS로 수집한 기사. 원본을 보존하고 가공 결과(원문 주소, 읽기시간, 카테고리)를 함께 둔다.
 * 원문 본문은 조회 성능을 위해 {@link ArticleContent}에 따로 둔다.
 */
@Entity
@Table(name = "article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity {

    public static final int MAX_GUID_LENGTH = 500;
    public static final int MAX_ORIGINAL_URL_LENGTH = 1000;
    public static final int MAX_SOURCE_URL_LENGTH = 1000;
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

    // 크롤링한 언론사 원문 주소. 원문을 가져오지 못했으면 NULL
    @Column(length = MAX_SOURCE_URL_LENGTH)
    private String sourceUrl;

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

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(precision = 7, scale = 3)
    private BigDecimal readingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ArticleDifficulty difficulty;

    @Column(length = 60)
    private String summaryOneLine;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "varchar(30)[]")
    private List<String> summaryKeywords;

    @Column(length = 50)
    private String summarySentence1;

    @Column(length = 50)
    private String summarySentence2;

    @Column(length = 50)
    private String summarySentence3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleProcessStatus processStatus;

    // 추천 후보 여부(RSS-08). 카테고리·난이도·요약·읽기시간이 모두 있어야 true가 된다(DB 제약 ck_article_recommendable)
    @Column(nullable = false)
    private boolean recommendable;

    // 같은 사건으로 묶여 추천에서 뺀 기사면, 남긴 기사(같은 사건 대표)의 ID
    private Long duplicateOfArticleId;

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

    public void markCleaned(String sourceUrl, BigDecimal readingMinutes) {
        this.sourceUrl = sourceUrl;
        this.readingMinutes = readingMinutes;
        this.processStatus = ArticleProcessStatus.CLEANED;
    }

    /**
     * 검증을 통과한 한 줄 요약·3문장 요약·핵심 키워드와 요약에 쓴 난이도를 저장한다(AI-02).
     */
    public void markSummarized(ArticleDifficulty difficulty, String oneLine, List<String> summary,
        List<String> keywords) {
        this.difficulty = difficulty;
        this.summaryOneLine = oneLine;
        this.summaryKeywords = List.copyOf(keywords);
        this.summarySentence1 = summary.get(0);
        this.summarySentence2 = summary.get(1);
        this.summarySentence3 = summary.get(2);
        this.processStatus = ArticleProcessStatus.SUMMARIZED;
        markReadyIfComplete();
    }

    /**
     * 앱의 원문 보기 주소. 크롤링한 언론사 원문 주소가 없으면 수집한 주소(구글 뉴스 링크)를 쓴다.
     */
    public String linkUrl() {
        return sourceUrl != null ? sourceUrl : originalUrl;
    }

    /**
     * 같은 사건의 다른 기사를 남기고 이 기사는 추천에서 뺀다.
     */
    public void markDuplicateOf(Long representativeArticleId) {
        this.duplicateOfArticleId = representativeArticleId;
        this.recommendable = false;
    }

    // 카테고리가 매핑되고 읽기시간까지 계산된 기사만 추천 후보가 된다(RSS-08)
    private void markReadyIfComplete() {
        if (categoryId != null && readingMinutes != null) {
            this.processStatus = ArticleProcessStatus.READY;
            this.recommendable = true;
        }
    }
}
