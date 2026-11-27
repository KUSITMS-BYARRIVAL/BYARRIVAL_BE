package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.user.dto.request.UserUpdateRequest;
import com.byarrival.domain.user.dto.request.WeeklyCommuteRequest.DailyCommuteRequest;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.entity.CommuteDays;
import com.byarrival.domain.user.entity.DailyCommute;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이 화면의 프로필·개인 설정 변경. 바꾼 값은 다음 노선부터 반영되고 이미 만든 노선의 스냅샷은 그대로다.
 * 모든 변경은 바꾼 뒤의 내 정보 전체를 돌려준다. 같은 회원의 변경이 서로 덮어쓰지 않도록 회원 행을 잠그고 처리한다.
 */
@Service
@RequiredArgsConstructor
public class UserSettingService {

    private final UserService userService;
    private final UserProfileValidator userProfileValidator;
    private final CommuteDayService commuteDayService;
    private final Clock clock;

    /**
     * 보낸 항목만 검증해서 저장한다. 하나라도 규칙에 어긋나면 아무것도 저장하지 않는다.
     */
    @Transactional
    public UserMeResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = userService.getActiveUserForUpdate(userId);
        if (request.nickname() != null) {
            user.changeNickname(userProfileValidator.validNickname(request.nickname()));
        }
        if (request.travelType() != null) {
            user.changeTravelType(request.travelType());
        }
        if (request.defaultCommuteMinutes() != null) {
            user.changeDefaultCommuteMinutes(userProfileValidator.validCommuteMinutes(request.defaultCommuteMinutes()));
        }
        return userService.getMe(userId);
    }

    @Transactional
    public UserMeResponse replaceInterestCategories(Long userId, List<Long> categoryIds) {
        userService.getActiveUserForUpdate(userId);
        userProfileValidator.validateCategories(categoryIds);
        userService.replaceInterestCategories(userId, categoryIds);
        return userService.getMe(userId);
    }

    /**
     * 보낸 요일만 이동하는 날로 저장하고 나머지 요일은 비운다. 모든 요일을 비워도 된다(모두 쉬는 날).
     * 노선 목표 시간과 출근 시각 알림에는 바로 반영하고, 스트릭 대상일에는 다음 서비스일부터 반영한다.
     */
    @Transactional
    public UserMeResponse replaceWeeklyCommute(Long userId, List<DailyCommuteRequest> days) {
        User user = userService.getActiveUserForUpdate(userId);
        Map<DayOfWeek, DailyCommute> weekly = new EnumMap<>(DayOfWeek.class);
        for (DailyCommuteRequest day : days) {
            userProfileValidator.validateDailyCommuteMinutes(day.minutes());
            DailyCommute previous = weekly.put(day.dayOfWeek(),
                new DailyCommute(day.startTime().truncatedTo(ChronoUnit.MINUTES), day.minutes()));
            if (previous != null) {
                throw new BusinessException(UserErrorCode.INVALID_WEEKLY_COMMUTE);
            }
        }
        commuteDayService.recordChange(userId, CommuteDayService.commuteDaysOf(user), CommuteDays.of(weekly.keySet()),
            ServiceDay.today(clock), LocalDateTime.now(clock));
        user.replaceWeeklyCommute(weekly);
        return userService.getMe(userId);
    }

    /**
     * 근무 지역을 바꾼다(MY-12). 바뀌면 변경 이력을 남긴다.
     */
    @Transactional
    public UserMeResponse changeRegion(Long userId, Integer sigunguId) {
        User user = userService.getActiveUserForUpdate(userId);
        userProfileValidator.validateRegion(sigunguId);
        userService.changeRegion(user, sigunguId);
        return userService.getMe(userId);
    }

    /**
     * 직군을 바꾼다. 원래 있던 직군 중 남는 것은 기존 순서대로 앞에 두고, 새로 고른 직군은 보낸 순서대로 뒤에 붙인다.
     * 이 순서가 홈 직군 칩 순서가 된다.
     */
    @Transactional
    public UserMeResponse replaceJobGroups(Long userId, List<JobGroup> jobGroups) {
        userService.getActiveUserForUpdate(userId);
        List<JobGroup> requested = jobGroups.stream().distinct().toList();
        List<JobGroup> ordered = new ArrayList<>();
        for (JobGroup existing : userService.findJobGroups(userId)) {
            if (requested.contains(existing)) {
                ordered.add(existing);
            }
        }
        for (JobGroup jobGroup : requested) {
            if (!ordered.contains(jobGroup)) {
                ordered.add(jobGroup);
            }
        }
        userService.replaceJobGroups(userId, ordered);
        return userService.getMe(userId);
    }
}
