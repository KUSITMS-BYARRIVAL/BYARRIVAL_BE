package com.byarrival.domain.term.entity;

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
 * 약관 동의·철회 이력. 상태를 바꿀 때 기존 행을 고치지 않고 새 행을 쌓으며, 가장 최근 행이 현재 상태다.
 */
@Entity
@Table(name = "user_terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long termsId;

    @Column(nullable = false)
    private boolean agreed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    private UserTermsAgreement(Long userId, Long termsId, boolean agreed, LocalDateTime agreedAt) {
        this.userId = userId;
        this.termsId = termsId;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
    }

    public static UserTermsAgreement record(Long userId, Long termsId, boolean agreed, LocalDateTime agreedAt) {
        return new UserTermsAgreement(userId, termsId, agreed, agreedAt);
    }
}
