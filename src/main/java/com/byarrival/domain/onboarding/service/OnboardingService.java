package com.byarrival.domain.onboarding.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.onboarding.dto.request.OnboardingUpdateRequest;
import com.byarrival.domain.onboarding.dto.response.OnboardingResponse;
import com.byarrival.domain.onboarding.exception.OnboardingErrorCode;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserProfileValidator;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 화면마다 입력한 값을 저장하고, 필수값이 모두 있으면 온보딩을 완료한다.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserService userService;
    private final UserProfileValidator userProfileValidator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OnboardingResponse getOnboarding(Long userId) {
        User user = userService.getActiveUser(userId);
        return response(user);
    }

    /**
     * 보낸 항목만 검증해서 저장한다. 하나라도 규칙에 어긋나면 아무것도 저장하지 않는다.
     * 마이 설정 변경과 겹쳐도 서로 덮어쓰지 않도록 회원 행을 잠그고 처리한다.
     */
    @Transactional
    public OnboardingResponse updateOnboarding(Long userId, OnboardingUpdateRequest request) {
        User user = userService.getActiveUserForUpdate(userId);
        if (request.nickname() != null) {
            user.changeNickname(userProfileValidator.validNickname(request.nickname()));
        }
        if (request.travelType() != null) {
            user.changeTravelType(request.travelType());
        }
        if (request.commuteStartTime() != null) {
            user.changeCommuteStartTime(request.commuteStartTime().truncatedTo(ChronoUnit.MINUTES));
        }
        if (request.defaultCommuteMinutes() != null) {
            user.changeDefaultCommuteMinutes(userProfileValidator.validCommuteMinutes(request.defaultCommuteMinutes()));
        }
        if (request.categoryIds() != null) {
            userProfileValidator.validateCategories(request.categoryIds());
            userService.replaceInterestCategories(userId, request.categoryIds());
        }
        if (request.jobGroups() != null) {
            // 같은 직군을 여러 번 보내면 처음 고른 순서로 한 번만 저장한다
            userService.replaceJobGroups(userId, request.jobGroups().stream().distinct().toList());
        }
        if (request.sigunguId() != null) {
            userProfileValidator.validateRegion(request.sigunguId());
            userService.changeRegion(user, request.sigunguId());
        }
        return response(user);
    }

    /**
     * 이동 유형, 이동 시작 시각, 기본 이동시간, 관심 분야 1개 이상, 직군 1개 이상, 근무 지역이 저장돼 있어야 완료한다.
     * 이미 완료했으면 필수값을 다시 확인하지 않고 그대로 성공한다. 필수값이 늘기 전에 완료한 회원(예: 근무 지역 없음)도 성공한다.
     */
    @Transactional
    public OnboardingResponse completeOnboarding(Long userId) {
        User user = userService.getActiveUserForUpdate(userId);
        if (user.isOnboardingCompleted()) {
            return response(user);
        }
        List<Long> categoryIds = userService.findInterestCategoryIds(userId);
        List<JobGroup> jobGroups = userService.findJobGroups(userId);
        boolean missing = user.getTravelType() == null
            || user.getCommuteStartTime() == null
            || user.getDefaultCommuteMinutes() == null
            || categoryIds.isEmpty()
            || jobGroups.isEmpty()
            || user.getRegionSigunguId() == null;
        if (missing) {
            throw new BusinessException(OnboardingErrorCode.ONBOARDING_INCOMPLETE);
        }
        user.completeOnboarding(LocalDateTime.now(clock));
        return OnboardingResponse.of(user, categoryIds, jobGroups, userService.findRegion(user));
    }

    private OnboardingResponse response(User user) {
        Long userId = user.getId();
        return OnboardingResponse.of(user, userService.findInterestCategoryIds(userId),
            userService.findJobGroups(userId), userService.findRegion(user));
    }
}
