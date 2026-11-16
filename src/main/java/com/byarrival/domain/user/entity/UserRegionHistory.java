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
 * 근무 지역 변경 이력(MY-12). 바뀐 뒤의 지역과 바뀐 시각(createdAt)을 남긴다.
 */
@Entity
@Table(name = "user_region_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRegionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer regionSigunguId;

    private UserRegionHistory(Long userId, Integer regionSigunguId) {
        this.userId = userId;
        this.regionSigunguId = regionSigunguId;
    }

    public static UserRegionHistory of(Long userId, Integer regionSigunguId) {
        return new UserRegionHistory(userId, regionSigunguId);
    }
}
