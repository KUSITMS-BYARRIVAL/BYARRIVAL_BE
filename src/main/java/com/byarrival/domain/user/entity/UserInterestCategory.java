package com.byarrival.domain.user.entity;

import com.byarrival.common.entity.BaseEntity;
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
 * 회원이 고른 관심 분야(서비스 카테고리).
 */
@Entity
@Table(name = "user_interest_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterestCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    private UserInterestCategory(Long userId, Long categoryId) {
        this.userId = userId;
        this.categoryId = categoryId;
    }

    public static UserInterestCategory of(Long userId, Long categoryId) {
        return new UserInterestCategory(userId, categoryId);
    }
}
