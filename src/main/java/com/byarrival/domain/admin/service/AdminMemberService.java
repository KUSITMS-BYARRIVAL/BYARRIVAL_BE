package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Item;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.MonthlyUsage;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.NotificationStatus;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Page;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Streak;
import com.byarrival.domain.admin.entity.AdminMemberAccessLog;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminMemberAccessLogRepository;
import com.byarrival.domain.admin.repository.AdminMemberRepository;
import com.byarrival.domain.admin.repository.AdminMemberRepository.MemberRow;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.notification.entity.NotificationCategory;
import com.byarrival.domain.notification.service.NotificationSettingService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.route.service.RouteStreakService.StreakDays;
import com.byarrival.domain.stat.dto.response.StatPeriod;
import com.byarrival.domain.stat.dto.response.StatSummaryResponse;
import com.byarrival.domain.stat.service.StatService;
import com.byarrival.domain.term.entity.TermsType;
import com.byarrival.domain.term.service.TermService;
import com.byarrival.domain.user.dto.response.DailyCommuteResponse;
import com.byarrival.domain.user.entity.OnboardingStatus;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 회원 목록·상세(MEMBER-01·02)와 상세 열람 기록(MEMBER-04).
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    static final int PAGE_SIZE = 20;

    private final AdminMemberRepository memberRepository;
    private final AdminMemberAccessLogRepository accessLogRepository;
    private final UserService userService;
    private final RouteStreakService routeStreakService;
    private final StatService statService;
    private final CategoryMappingService categoryMappingService;
    private final NotificationSettingService notificationSettingService;
    private final TermService termService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page getMembers(AdminMemberSearchRequest search) {
        long total = memberRepository.count(search);
        int page = search.page();
        List<MemberRow> rows = memberRepository.findPage(search, PAGE_SIZE, (page - 1L) * PAGE_SIZE);
        Map<Long, StreakDays> streaks = routeStreakService.findStreaks(rows.stream().map(MemberRow::id).toList(),
            ServiceDay.today(clock));
        List<Item> members = rows.stream().map(row -> Item.of(row, streaks.get(row.id()))).toList();
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
        LocalDateTime now = LocalDateTime.now(clock);
        accessLogRepository.saveAndFlush(AdminMemberAccessLog.of(adminId, memberId, now));

        LocalDate today = ServiceDay.today(clock);
        StreakDays streak = routeStreakService.findStreaks(List.of(memberId), today).get(memberId);
        StatSummaryResponse month = statService.getSummary(memberId, StatPeriod.MONTH);
        List<DailyCommuteResponse> weekly = user.getWeeklyCommute().entrySet().stream()
            .map(entry -> DailyCommuteResponse.of(entry.getKey(), entry.getValue()))
            .toList();
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        List<String> interests = userService.findInterestCategoryIds(memberId).stream()
            .map(categoryNames::get)
            .filter(Objects::nonNull)
            .toList();
        NotificationStatus notification = new NotificationStatus(user.getNotificationPermission(),
            notificationSettingService.isEnabled(memberId, NotificationCategory.ACTIVITY),
            termService.hasAgreedLatest(memberId, TermsType.MARKETING));
        return new Detail(user.getId(), user.getNickname(), user.getProvider(), user.getTravelType(),
            user.getCreatedAt(), user.getOnboardingStatus() == OnboardingStatus.COMPLETED,
            user.getDefaultCommuteMinutes(), weekly, interests, Streak.from(streak),
            new MonthlyUsage(month.completedCount(), month.usedMinutes()), notification);
    }
}
