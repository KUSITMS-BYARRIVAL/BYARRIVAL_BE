package com.byarrival.domain.admin.dto.response;

import com.byarrival.domain.admin.repository.AdminMemberRepository.MemberRow;
import com.byarrival.domain.route.service.RouteStreakService.StreakDays;
import com.byarrival.domain.user.dto.response.DailyCommuteResponse;
import com.byarrival.domain.user.entity.NotificationPermission;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 회원 API 응답 모음. 소셜 계정 식별자와 토큰 같은 값은 넣지 않는다.
 */
public final class AdminMemberResponses {

    private AdminMemberResponses() {
    }

    /**
     * @param page          현재 페이지(1부터)
     * @param totalElements 검색 조건에 맞는 회원 수
     */
    public record Page(List<Item> members, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    public record Item(Long memberId, String nickname, SocialProvider provider, TravelType travelType,
                       LocalDateTime joinedAt, Streak streak) {

        public static Item of(MemberRow row, StreakDays streak) {
            return new Item(row.id(), row.nickname(), row.provider(), row.travelType(), row.joinedAt(),
                Streak.from(streak));
        }
    }

    public record Streak(int current, int longest) {

        public static Streak from(StreakDays days) {
            return new Streak(days.current(), days.longest());
        }
    }

    /**
     * @param defaultCommuteMinutes 기본 이동시간(분)
     * @param weeklyCommute         이동하는 요일만 담는다
     * @param interestCategories    관심 분야 이름
     */
    public record Detail(Long memberId, String nickname, SocialProvider provider, TravelType travelType,
                         LocalDateTime joinedAt, boolean onboardingCompleted, Integer defaultCommuteMinutes,
                         List<DailyCommuteResponse> weeklyCommute, List<String> interestCategories, Streak streak,
                         MonthlyUsage thisMonth, NotificationStatus notification) {
    }

    public record MonthlyUsage(long completedCount, long usedMinutes) {
    }

    /**
     * @param permission      OS 알림 권한. 아직 묻지 않았으면 null
     * @param activityEnabled 앱의 알림 수신 설정
     * @param marketingAgreed 마케팅 정보 수신 동의
     */
    public record NotificationStatus(NotificationPermission permission, boolean activityEnabled,
                                     boolean marketingAgreed) {
    }
}
