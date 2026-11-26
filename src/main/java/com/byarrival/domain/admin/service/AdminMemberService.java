package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Item;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Job;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Page;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Region;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.RestrictionResult;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Streak;
import com.byarrival.domain.admin.entity.AdminMemberAccessLog;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminMemberAccessLogRepository;
import com.byarrival.domain.admin.repository.AdminMemberRepository;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.route.service.RouteRecordService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.route.service.RouteStreakService.StreakDays;
import com.byarrival.domain.user.dto.response.DailyCommuteResponse;
import com.byarrival.domain.user.entity.OnboardingStatus;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 회원 목록·상세(MEMBER-01·02, ADM-S07)와 상세 열람 기록(MEMBER-04). 이용 제한은 AdminMemberRestrictionService가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    static final int PAGE_SIZE = 20;

    private final AdminMemberRepository memberRepository;
    private final AdminMemberAccessLogRepository accessLogRepository;
    private final UserService userService;
    private final RouteStreakService routeStreakService;
    private final RouteRecordService routeRecordService;
    private final CategoryMappingService categoryMappingService;
    private final AdminMemberRestrictionService restrictionService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page getMembers(AdminMemberSearchRequest search) {
        LocalDateTime now = LocalDateTime.now(clock);
        long total = memberRepository.count(search, now);
        int page = search.page();
        List<Item> members = memberRepository.findPage(search, now, PAGE_SIZE, (page - 1L) * PAGE_SIZE).stream()
            .map(Item::from)
            .toList();
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        return new Page(members, page, PAGE_SIZE, total, totalPages, page < totalPages);
    }

    /**
     * 열람 기록을 먼저 남긴다. 기록을 저장하지 못하면 회원 정보도 돌려주지 않는다.
     */
    @Transactional
    public Detail getMember(Long adminId, Long memberId) {
        User user = userService.findActiveById(memberId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_MEMBER_NOT_FOUND));
        accessLogRepository.saveAndFlush(AdminMemberAccessLog.of(adminId, memberId, LocalDateTime.now(clock)));

        StreakDays streak = routeStreakService.findStreaks(List.of(memberId), ServiceDay.today(clock)).get(memberId);
        List<DailyCommuteResponse> weekly = user.getWeeklyCommute().entrySet().stream()
            .map(entry -> DailyCommuteResponse.of(entry.getKey(), entry.getValue()))
            .toList();
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        List<String> interests = userService.findInterestCategoryIds(memberId).stream()
            .map(categoryNames::get)
            .filter(Objects::nonNull)
            .toList();
        Job jobGroup = Job.from(userService.findJobGroups(memberId).stream().findFirst().orElse(null));
        RestrictionResult restriction = restrictionService.getRestriction(memberId);
        return new Detail(user.getId(), user.getNickname(), user.getProvider(), user.getTravelType(),
            user.getCreatedAt(), user.getOnboardingStatus() == OnboardingStatus.COMPLETED,
            routeRecordService.findLastStartedAt(memberId).orElse(null), user.getDefaultCommuteMinutes(), weekly,
            jobGroup, Region.from(userService.findRegion(user)), interests, Streak.from(streak),
            restriction.status(), restriction.restriction(), restriction.restrictionHistory());
    }
}
