package com.byarrival.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무 지역의 시/군/구. 회원은 이 중 하나를 고른다. 마이그레이션으로 넣는 고정 데이터다.
 */
@Entity
@Table(name = "region_sigungu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionSigungu {

    @Id
    private Integer id;

    @Column(nullable = false)
    private Short sidoId;

    @Column(nullable = false, length = 20)
    private String name;
}
