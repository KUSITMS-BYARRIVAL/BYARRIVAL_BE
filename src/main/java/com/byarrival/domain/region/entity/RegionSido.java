package com.byarrival.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무 지역의 시/도. 마이그레이션으로 넣는 고정 데이터이며 id 순서가 화면 순서다.
 */
@Entity
@Table(name = "region_sido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionSido {

    @Id
    private Short id;

    // 화면에 보이는 약칭. 예: 서울
    @Column(nullable = false, length = 10)
    private String name;

    // 예: 서울특별시
    @Column(nullable = false, length = 20)
    private String fullName;
}
