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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
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
    @Column(length = 20)
    private TravelType travelType;

    private LocalTime commuteStartTime;

    private Integer defaultCommuteMinutes;

    // 요일별 이동 시작 시각·이동시간. 둘 다 비어 있는 요일은 이동하지 않는 날이다
    private LocalTime monStartTime;
    private Integer monMinutes;
    private LocalTime tueStartTime;
    private Integer tueMinutes;
    private LocalTime wedStartTime;
    private Integer wedMinutes;
    private LocalTime thuStartTime;
    private Integer thuMinutes;
    private LocalTime friStartTime;
    private Integer friMinutes;
    private LocalTime satStartTime;
    private Integer satMinutes;
    private LocalTime sunStartTime;
    private Integer sunMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus;

    private LocalDateTime onboardedAt;

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

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeTravelType(TravelType travelType) {
        this.travelType = travelType;
    }

    public void changeCommuteStartTime(LocalTime commuteStartTime) {
        this.commuteStartTime = commuteStartTime;
    }

    public void changeDefaultCommuteMinutes(int defaultCommuteMinutes) {
        this.defaultCommuteMinutes = defaultCommuteMinutes;
    }

    /**
     * 이미 완료했으면 처음 완료한 시각을 유지한다.
     * 처음 완료할 때 요일별 설정이 비어 있으면 온보딩에서 받은 출근 시각·기본 이동시간을 월~금 초깃값으로 넣는다.
     */
    public void completeOnboarding(LocalDateTime now) {
        if (isOnboardingCompleted()) {
            return;
        }
        this.onboardingStatus = OnboardingStatus.COMPLETED;
        this.onboardedAt = now;
        if (getWeeklyCommute().isEmpty() && commuteStartTime != null && defaultCommuteMinutes != null) {
            Map<DayOfWeek, DailyCommute> weekdays = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek day : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
                weekdays.put(day, new DailyCommute(commuteStartTime, defaultCommuteMinutes));
            }
            replaceWeeklyCommute(weekdays);
        }
    }

    /**
     * 설정한 요일만 담는다. 비어 있는 요일은 이동하지 않는 날이다.
     */
    public Map<DayOfWeek, DailyCommute> getWeeklyCommute() {
        Map<DayOfWeek, DailyCommute> weekly = new EnumMap<>(DayOfWeek.class);
        putIfSet(weekly, DayOfWeek.MONDAY, monStartTime, monMinutes);
        putIfSet(weekly, DayOfWeek.TUESDAY, tueStartTime, tueMinutes);
        putIfSet(weekly, DayOfWeek.WEDNESDAY, wedStartTime, wedMinutes);
        putIfSet(weekly, DayOfWeek.THURSDAY, thuStartTime, thuMinutes);
        putIfSet(weekly, DayOfWeek.FRIDAY, friStartTime, friMinutes);
        putIfSet(weekly, DayOfWeek.SATURDAY, satStartTime, satMinutes);
        putIfSet(weekly, DayOfWeek.SUNDAY, sunStartTime, sunMinutes);
        return weekly;
    }

    /**
     * 요일별 설정을 통째로 바꾼다. 담지 않은 요일은 이동하지 않는 날로 비운다. 값 검증은 호출하는 쪽에서 한다.
     */
    public void replaceWeeklyCommute(Map<DayOfWeek, DailyCommute> weekly) {
        DailyCommute mon = weekly.get(DayOfWeek.MONDAY);
        DailyCommute tue = weekly.get(DayOfWeek.TUESDAY);
        DailyCommute wed = weekly.get(DayOfWeek.WEDNESDAY);
        DailyCommute thu = weekly.get(DayOfWeek.THURSDAY);
        DailyCommute fri = weekly.get(DayOfWeek.FRIDAY);
        DailyCommute sat = weekly.get(DayOfWeek.SATURDAY);
        DailyCommute sun = weekly.get(DayOfWeek.SUNDAY);
        this.monStartTime = startTime(mon);
        this.monMinutes = minutes(mon);
        this.tueStartTime = startTime(tue);
        this.tueMinutes = minutes(tue);
        this.wedStartTime = startTime(wed);
        this.wedMinutes = minutes(wed);
        this.thuStartTime = startTime(thu);
        this.thuMinutes = minutes(thu);
        this.friStartTime = startTime(fri);
        this.friMinutes = minutes(fri);
        this.satStartTime = startTime(sat);
        this.satMinutes = minutes(sat);
        this.sunStartTime = startTime(sun);
        this.sunMinutes = minutes(sun);
    }

    private static void putIfSet(Map<DayOfWeek, DailyCommute> weekly, DayOfWeek day, LocalTime startTime,
        Integer minutes) {
        if (startTime != null && minutes != null) {
            weekly.put(day, new DailyCommute(startTime, minutes));
        }
    }

    private static LocalTime startTime(DailyCommute commute) {
        return commute == null ? null : commute.startTime();
    }

    private static Integer minutes(DailyCommute commute) {
        return commute == null ? null : commute.minutes();
    }

    public boolean isOnboardingCompleted() {
        return onboardingStatus == OnboardingStatus.COMPLETED;
    }
}
