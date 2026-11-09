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
 * 회원. 소셜 계정(provider, providerUserId)으로 식별한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    @Column(length = 30)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private NotificationPermission notificationPermission;

    private User(SocialProvider provider, String providerUserId) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.onboardingStatus = OnboardingStatus.IN_PROGRESS;
        this.status = UserStatus.ACTIVE;
    }

    public static User signUp(SocialProvider provider, String providerUserId) {
        return new User(provider, providerUserId);
    }

    public void updateNotificationPermission(NotificationPermission permission) {
        this.notificationPermission = permission;
    }

    public boolean isOnboardingCompleted() {
        return onboardingStatus == OnboardingStatus.COMPLETED;
    }
}
