package com.byarrival.domain.user.entity;

import com.byarrival.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 고른 직군. sortOrder는 고른 순서(1부터)이며 홈 직군 칩 순서가 된다.
 */
@Entity
@Table(name = "user_job_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJobGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobGroup jobGroup;

    @Column(nullable = false)
    private short sortOrder;

    private UserJobGroup(Long userId, JobGroup jobGroup, short sortOrder) {
        this.userId = userId;
        this.jobGroup = jobGroup;
        this.sortOrder = sortOrder;
    }

    public static UserJobGroup of(Long userId, JobGroup jobGroup, int sortOrder) {
        return new UserJobGroup(userId, jobGroup, (short) sortOrder);
    }
}
