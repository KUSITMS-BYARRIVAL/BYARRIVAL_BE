package com.byarrival.domain.feed.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 대상 언론사 RSS Feed. 활성 Feed만 수집한다.
 */
@Entity
@Table(name = "rss_feed")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RssFeed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String pressName;

    @Column(nullable = false, length = 500, unique = true)
    private String feedUrl;

    @Column(length = 50)
    private String rssCategory;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime lastFetchedAt;

    private RssFeed(String pressName, String feedUrl, String rssCategory) {
        this.pressName = pressName;
        this.feedUrl = feedUrl;
        this.rssCategory = rssCategory;
        this.active = true;
    }

    public static RssFeed create(String pressName, String feedUrl, String rssCategory) {
        return new RssFeed(pressName, feedUrl, rssCategory);
    }

    public void deactivate() {
        this.active = false;
    }
}
