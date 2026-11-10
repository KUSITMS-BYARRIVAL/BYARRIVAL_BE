package com.byarrival.domain.route.entity;

import com.byarrival.common.entity.BaseEntity;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노선 생성 시 고정한 후보 카드 한 장과 담기 상태. 난이도·읽기시간은 생성 시점 값으로 고정한다.
 */
@Entity
@Table(name = "route_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long routeId;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private int poolOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ArticleDifficulty difficulty;

    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal readingMinutes;

    @Column(nullable = false)
    private boolean recommended;

    private Integer cartOrder;

    private LocalDateTime cartedAt;

    @Builder
    private RouteCard(Long routeId, Long articleId, int poolOrder, ArticleDifficulty difficulty,
        BigDecimal readingMinutes, boolean recommended) {
        this.routeId = routeId;
        this.articleId = articleId;
        this.poolOrder = poolOrder;
        this.difficulty = difficulty;
        this.readingMinutes = readingMinutes;
        this.recommended = recommended;
    }

    public boolean isCarted() {
        return cartOrder != null;
    }

    public void addToCart(int cartOrder, LocalDateTime now) {
        this.cartOrder = cartOrder;
        this.cartedAt = now;
    }

    public void removeFromCart() {
        this.cartOrder = null;
        this.cartedAt = null;
    }
}
